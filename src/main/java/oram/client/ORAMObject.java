package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.structure.*;
import oram.messages.EvictionORAMMessage;
import oram.messages.ORAMMessage;
import oram.messages.StashPathORAMMessage;
import oram.security.EncryptionManager;
import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;
import oram.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.security.SecureRandom;
import java.util.*;

public abstract class ORAMObject {
	protected final Logger logger = LoggerFactory.getLogger("oram");
	protected final Logger measurementLogger = LoggerFactory.getLogger("measurement");
	protected final ConfidentialServiceProxy serviceProxy;
	protected final int oramId;
	protected final ORAMContext oramContext;
	protected final EncryptionManager encryptionManager;
	private final SecureRandom rndGenerator;
	protected boolean isMeasure;
	private Map<Integer, int[]> allPaths;
	private Map<Integer, int[]> bucketToPaths;
	private OngoingAccessContext ongoingAccessContext;

	protected long globalDelayRemoteInvocation;

	public ORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
					  EncryptionManager encryptionManager) {
		this.serviceProxy = serviceProxy;
		this.oramId = oramId;
		this.oramContext = oramContext;
		this.encryptionManager = encryptionManager;
		this.rndGenerator = new SecureRandom("oram".getBytes());
		preComputeTreeLocations(oramContext.getTreeHeight());
	}

	private void preComputeTreeLocations(int treeHeight) {
		int nPaths = 1 << treeHeight;
		int treeSize = ORAMUtils.computeNumberOfNodes(treeHeight);
		allPaths = new HashMap<>(nPaths);
		Map<Integer, Set<Integer>> tempBucketToPaths = new HashMap<>(treeSize);
		for (int pathId = 0; pathId < nPaths; pathId++) {
			int[] pathLocations = ORAMUtils.computePathLocations(pathId, treeHeight);
			allPaths.put(pathId, pathLocations);
			for (int pathLocation : pathLocations) {
				tempBucketToPaths.computeIfAbsent(pathLocation, k -> new HashSet<>()).add(pathId);
			}
		}
		bucketToPaths = new HashMap<>(treeSize);
		for (int i = 0; i < treeSize; i++) {
			Set<Integer> possiblePaths = tempBucketToPaths.get(i);
			int[] possiblePathsArray = new int[possiblePaths.size()];
			int j = 0;
			for (int possiblePath : possiblePaths) {
				possiblePathsArray[j++] = possiblePath;
			}
			bucketToPaths.put(i, possiblePathsArray);
		}
	}

	public void measure() {
		isMeasure = true;
	}

	/**
	 * Read the memory address.
	 *
	 * @param address Memory address.
	 * @return Content located at the memory address.
	 */
	public byte[] readMemory(int address) {
		if (address < 0 || oramContext.getTreeSize() <= address)
			return null;
		return access(Operation.READ, address, null);
	}

	/**
	 * Write content to the memory address.
	 *
	 * @param address Memory address.
	 * @param content Content to write.
	 * @return Old content located at the memory address.
	 */
	public byte[] writeMemory(int address, byte[] content) {
		if (address < 0 || oramContext.getTreeSize() <= address)
			return null;
		return access(Operation.WRITE, address, content);
	}

	private byte[] access(Operation op, int address, byte[] newContent) {
		reset();
		ongoingAccessContext = new OngoingAccessContext(address, op, newContent);

		long start, end, delay;
		start = System.nanoTime();
		PositionMaps oldPositionMaps = getPositionMaps();
		if (oldPositionMaps == null) {
			logger.error("Position map of oram {} is null", oramId);
			return null;
		}
		measurementLogger.info("M-receivedPM: {}", oldPositionMaps.getPositionMaps().size());

		PositionMap mergedPositionMap = mergePositionMaps(oldPositionMaps);
		if (mergedPositionMap == null) {
			logger.error("Failed to merge position maps of oram {}", oramId);
			return null;
		}

		ongoingAccessContext.setMergedPositionMap(mergedPositionMap);
		ongoingAccessContext.setNewVersionId(oldPositionMaps.getNewVersionId());

		int pathId = getPathId(mergedPositionMap, address);

		ongoingAccessContext.setAccessedPathId(pathId);

		end = System.nanoTime();
		delay = end - start;
		measurementLogger.info("M-map: {}", delay);

		start = System.nanoTime();
		Stash mergedStash = getPS();
		ongoingAccessContext.setMergedStash(mergedStash);
		end = System.nanoTime();
		delay = end - start;
		measurementLogger.info("M-ps: {}", delay);

		start = System.nanoTime();
		computeNewLocations();
		boolean isEvicted = evict();
		end = System.nanoTime();
		delay = end - start;
		measurementLogger.info("M-eviction: {}", delay);
		measurementLogger.info("M-serviceCall: {}", globalDelayRemoteInvocation);
		if (!isEvicted) {
			logger.error("Failed to do eviction on oram {}", oramId);
		}
		return ongoingAccessContext.getOldContent();
	}

	protected void reset() {
		globalDelayRemoteInvocation = 0;
		ongoingAccessContext = null;
	}

	public int getPathId(PositionMap mergedPositionMap, int address) {
		int bucketId = mergedPositionMap.getPathAt(address);
		logger.debug("Getting pathId {} for address {} and version {}", bucketId, address,
				mergedPositionMap.getVersionIdAt(address));
		if (bucketId == ORAMUtils.DUMMY_PATH) {
			ongoingAccessContext.setIsRealAccess(false);
			return generateRandomPathId();
		}
		int[] possiblePaths = bucketToPaths.get(bucketId);
		return possiblePaths[rndGenerator.nextInt(possiblePaths.length)];
	}

	public Stash getPS() {
		int pathId = ongoingAccessContext.getAccessedPathId();
		Operation op = ongoingAccessContext.getOperation();
		int accessedAddress = ongoingAccessContext.getAddress();
		byte[] newContent = ongoingAccessContext.getNewContent();
		int newVersionId = ongoingAccessContext.getNewVersionId();
		PositionMap mergedPositionMap = ongoingAccessContext.getMergedPositionMap();

		StashesAndPaths stashesAndPaths = getStashesAndPaths(pathId);
		if (stashesAndPaths == null) {
			logger.error("States and paths of oram {} are null", oramId);
			return null;
		}

		Stash mergedStash = mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths(),
				mergedPositionMap);

		Block block = mergedStash.getBlock(accessedAddress);

		if (ongoingAccessContext.isRealAccess() && op == Operation.READ) {
			if (block == null) {
				logger.error("[client {}] Reading address {} from pathId {}", serviceProxy.getProcessId(), accessedAddress, pathId);
				logger.error("[client {}] {}", serviceProxy.getProcessId(), stashesAndPaths);
				logger.error("[client {}] {}", serviceProxy.getProcessId(), mergedPositionMap.toString());
				logger.error("[client {}] {}", serviceProxy.getProcessId(), mergedStash);
				throw new IllegalStateException("Block is null");
				//System.exit(-1);
			} else {
				ongoingAccessContext.setOldContent(block.getContent());
			}
		} else if (op == Operation.WRITE) {
			if (block == null) {
				block = new Block(oramContext.getBlockSize(), accessedAddress, newVersionId, newContent);
				mergedStash.putBlock(block);
			} else {
				ongoingAccessContext.setOldContent(block.getContent());
				block.setContent(newContent);
				block.setVersionId(newVersionId);
			}
		}

		return mergedStash;
	}

	private void computeNewLocations() {
		int address = ongoingAccessContext.getAddress();
		PositionMap positionMap = ongoingAccessContext.getMergedPositionMap();
		int accessedPathId = ongoingAccessContext.getAccessedPathId();

		int currentBucket = positionMap.getPathAt(address);
		int[] accessedPathLocations = allPaths.get(accessedPathId);

		int currentBucketIndex = -1;
		if (currentBucket == ORAMUtils.DUMMY_PATH) {
			currentBucketIndex = rndGenerator.nextInt(oramContext.getTreeLevels());
			currentBucket = accessedPathLocations[currentBucketIndex];
		} else {
			for (int i = 0; i < accessedPathLocations.length; i++) {
				if (accessedPathLocations[i] == currentBucket) {
					currentBucketIndex = i;
					break;
				}
			}
		}
		if (currentBucketIndex == -1) {
			logger.error("Current bucket {} is not in the accessed path {}", currentBucket, accessedPathId);
			throw new IllegalStateException("Current bucket is not in the accessed path");
		}
		int newBucketLocation = allPaths.get(accessedPathId)[
				rndGenerator.nextInt(oramContext.getTreeLevels() - currentBucketIndex) + currentBucketIndex];
		ongoingAccessContext.setAccessedAddressNewBucketLocation(newBucketLocation);

		int treeSize = ORAMUtils.computeNumberOfNodes(oramContext.getTreeHeight());
		int substitutionNewBucketLocation = rndGenerator.nextInt(treeSize - newBucketLocation) + newBucketLocation;
		ongoingAccessContext.setSubstitutedBlockNewBucketLocation(substitutionNewBucketLocation);
	}

	public boolean evict() {
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		Stash remainingBlocks = new Stash(oramContext.getBlockSize());
		populatePath(path, remainingBlocks);

		PositionMap updatedPositionMap = ongoingAccessContext.getUpdatedPositionMap();

		EncryptedStash encryptedStash = encryptionManager.encryptStash(remainingBlocks);
		EncryptedPositionMap encryptedPositionMap = encryptionManager.encryptPositionMap(updatedPositionMap);
		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(oramContext, path);

		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath);
	}

	private void populatePath(Map<Integer, Bucket> pathToPopulate, Stash remainingBlocks) {
		Stash stash = ongoingAccessContext.getMergedStash();
		PositionMap positionMap = ongoingAccessContext.getMergedPositionMap();
		int[] accessedPathLocations = allPaths.get(ongoingAccessContext.getAccessedPathId());
		int accessedBlockAddress = ongoingAccessContext.getAddress();
		int accessedAddressNewBucketLocation = ongoingAccessContext.getAccessedAddressNewBucketLocation();

		for (int pathLocation : accessedPathLocations) {
			pathToPopulate.put(pathLocation, new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize()));
		}

		boolean isSubstituted = false;

		for (Block block : stash.getBlocks()) {
			int address = block.getAddress();
			int bucketId = positionMap.getPathAt(address);
			if (address == accessedBlockAddress) {
				logger.debug("Block {} was moved up from bucket {} to bucket {} in path {}", address, bucketId,
						accessedAddressNewBucketLocation, ongoingAccessContext.getAccessedPathId());
				bucketId = accessedAddressNewBucketLocation;
				positionMap = updatePositionMap(ongoingAccessContext.getOperation(), positionMap,
						ongoingAccessContext.isRealAccess(), address, accessedAddressNewBucketLocation,
						ongoingAccessContext.getNewVersionId());
			} else if (bucketId == accessedAddressNewBucketLocation && !isSubstituted) {
				logger.debug("Block {} was moved down from bucket {} to bucket {}", address,
						bucketId, ongoingAccessContext.getSubstitutedBlockNewBucketLocation());
				bucketId = ongoingAccessContext.getSubstitutedBlockNewBucketLocation();
				positionMap = updatePositionMap(ongoingAccessContext.getOperation(), positionMap,
						ongoingAccessContext.isRealAccess(), address,
						ongoingAccessContext.getSubstitutedBlockNewBucketLocation(),
						ongoingAccessContext.getNewVersionId());
				isSubstituted = true;
			}

			ongoingAccessContext.setUpdatedPositionMap(positionMap);

			int pathIdForBucket = bucketToPaths.get(bucketId)[0];
			int[] pathForBucket = allPaths.get(pathIdForBucket);

			boolean isPathEmpty = false;
			for (int i = 0; i < oramContext.getTreeLevels(); i++) {
				if (accessedPathLocations[i] == pathForBucket[i] && bucketId >= accessedPathLocations[i]) {
					Bucket bucket = pathToPopulate.get(accessedPathLocations[i]);
					if (bucket.putBlock(block)) {
						isPathEmpty = true;
						break;
					}
				}
			}
			if (!isPathEmpty) {
				remainingBlocks.putBlock(block);
			}
		}
	}

	private boolean sendEvictionRequest(EncryptedStash encryptedStash, EncryptedPositionMap encryptedPositionMap,
										Map<Integer, EncryptedBucket> encryptedPath) {
		try {
			EvictionORAMMessage request = new EvictionORAMMessage(oramId, encryptedStash, encryptedPositionMap, encryptedPath);
			byte[] serializedDataRequest = ORAMUtils.serializeRequest(ServerOperationType.EVICTION, request);

			long start, end, delay;
			start = System.nanoTime();
			boolean isSuccessful = sendRequestData(serializedDataRequest);
			end = System.nanoTime();
			delay = end - start;
			if (!isSuccessful) {
				return false;
			}

			int hash = serviceProxy.getProcessId() + Arrays.hashCode(serializedDataRequest) * 32;
			ORAMMessage dataHashRequest = new ORAMMessage(hash);//Sending request hash as oramId (not ideal implementation)
			byte[] serializedEvictionRequest = ORAMUtils.serializeRequest(ServerOperationType.EVICTION, dataHashRequest);
			if (serializedEvictionRequest == null) {
				return false;
			}

			start = System.nanoTime();
			Response response = serviceProxy.invokeOrdered(serializedEvictionRequest);
			end = System.nanoTime();
			delay += end - start;

			if (response == null || response.getPlainData() == null) {
				return false;
			}
			globalDelayRemoteInvocation += delay;
			measurementLogger.info("M-evict: {}", delay);
			Status status = Status.getStatus(response.getPlainData()[0]);
			return status != Status.FAILED;
		} catch (SecretSharingException e) {
			return false;
		}
	}

	private boolean sendRequestData(byte[] serializedDataRequest) throws SecretSharingException {
		if (serializedDataRequest == null) {
			return false;
		}
		Response response = serviceProxy.invokeUnordered(serializedDataRequest);
		if (response == null || response.getPlainData() == null) {
			return false;
		}
		Status status = Status.getStatus(response.getPlainData()[0]);
		return status != Status.FAILED;
	}

	private int generateRandomPathId() {
		return rndGenerator.nextInt(1 << oramContext.getTreeHeight()); //2^height
	}

	private Stash mergeStashesAndPaths(Stash[] stashes, Bucket[] paths, PositionMap mergedPositionMap) {
		Map<Integer, Block> recentBlocks = new HashMap<>();

		mergeStashes(recentBlocks, stashes, mergedPositionMap);
		mergePaths(recentBlocks, paths, mergedPositionMap);

		Stash mergedStash = new Stash(oramContext.getBlockSize());
		for (Block recentBlock : recentBlocks.values()) {
			mergedStash.putBlock(recentBlock);
		}
		return mergedStash;
	}

	private void mergePaths(Map<Integer, Block> recentBlocks, Bucket[] paths,
							PositionMap mergedPositionMap) {
		for (Bucket bucket : paths) {
			if (bucket == null)
				continue;
			for (Block block : bucket.readBucket()) {
				if (block == null)
					continue;
				int blockAddress = block.getAddress();
				int blockVersionId = block.getVersionId();
				if (blockVersionId == mergedPositionMap.getVersionIdAt(blockAddress)) {
					recentBlocks.put(blockAddress, block);
				}
			}
		}
	}

	private void mergeStashes(Map<Integer, Block> recentBlocks, Stash[] stashes,
							  PositionMap mergedPositionMap) {
		for (Stash stash : stashes) {
			if (stash == null) {
				logger.warn("Stash is null");
				continue;
			}
			for (Block block : stash.getBlocks()) {
				int blockAddress = block.getAddress();
				int blockVersionId = block.getVersionId();
				if (blockVersionId == mergedPositionMap.getVersionIdAt(blockAddress)) {
					recentBlocks.put(blockAddress, block);
				}
			}
		}
	}

	private StashesAndPaths getStashesAndPaths(int pathId) {
		try {
			ORAMMessage request = new StashPathORAMMessage(oramId, pathId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_STASH_AND_PATH, request);
			if (serializedRequest == null) {
				return null;
			}

			long start, end, delay;
			start = System.nanoTime();
			Response response = serviceProxy.invokeOrderedHashed(serializedRequest);
			end = System.nanoTime();
			if (response == null || response.getPlainData() == null) {
				return null;
			}
			delay = end - start;
			globalDelayRemoteInvocation += delay;
			measurementLogger.info("M-getPS: {}", delay);

			return encryptionManager.decryptStashesAndPaths(oramContext, response.getPlainData());
		} catch (SecretSharingException e) {
			return null;
		}
	}
	protected abstract PositionMap updatePositionMap(Operation op, PositionMap mergedPositionMap, boolean isRealAccess, int address,
													 int newPathId, int newVersionId);

	protected abstract PositionMap mergePositionMaps(PositionMaps oldPositionMaps);

	protected abstract PositionMaps getPositionMaps();
}
