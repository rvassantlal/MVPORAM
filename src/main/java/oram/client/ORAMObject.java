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
	byte[] oldContent = null;
	private boolean isRealAccess;
	protected boolean isMeasure;
	private Map<Integer, int[]> allPaths;
	private Map<Integer, int[]> bucketToPaths;

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

		int pathId = getPathId(mergedPositionMap, address);

		end = System.nanoTime();
		delay = end - start;
		measurementLogger.info("M-map: {}", delay);

		start = System.nanoTime();
		Stash mergedStash = getPS(pathId, op, address, newContent, oldPositionMaps.getNewVersionId(), mergedPositionMap);
		end = System.nanoTime();
		delay = end - start;
		measurementLogger.info("M-ps: {}", delay);

		start = System.nanoTime();
		PositionMap updatedPositionMap = updateLocations(address, op, pathId, mergedPositionMap, oldPositionMaps.getNewVersionId());
		boolean isEvicted = evict(updatedPositionMap, mergedStash, pathId);
		end = System.nanoTime();
		delay = end - start;
		measurementLogger.info("M-eviction: {}", delay);
		measurementLogger.info("M-serviceCall: {}", globalDelayRemoteInvocation);
		if (!isEvicted) {
			logger.error("Failed to do eviction on oram {}", oramId);
		}
		return oldContent;
	}

	protected void reset() {
		oldContent = null;
		isRealAccess = true;
		globalDelayRemoteInvocation = 0;
	}

	public int getPathId(PositionMap mergedPositionMap, int address) {
		int bucketId = mergedPositionMap.getPathAt(address);
		logger.debug("Getting pathId {} for address {} and version {}", bucketId, address,
				mergedPositionMap.getVersionIdAt(address));
		if (bucketId == ORAMUtils.DUMMY_PATH) {
			this.isRealAccess = false;
			return generateRandomPathId();
		}
		int[] possiblePaths = bucketToPaths.get(bucketId);
		return possiblePaths[rndGenerator.nextInt(possiblePaths.length)];
	}

	public Stash getPS(int pathId, Operation op, int address, byte[] newContent,
					   int newVersionId, PositionMap mergedPositionMap) {
		StashesAndPaths stashesAndPaths = getStashesAndPaths(pathId);
		if (stashesAndPaths == null) {
			logger.error("States and paths of oram {} are null", oramId);
			return null;
		}

		Stash mergedStash = mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths(),
				mergedPositionMap);

		Block block = mergedStash.getBlock(address);

		if (this.isRealAccess && op == Operation.READ) {
			if (block == null) {
				logger.error("[client {}] Reading address {} from pathId {}", serviceProxy.getProcessId(), address, pathId);
				logger.error("[client {}] {}", serviceProxy.getProcessId(), stashesAndPaths);
				logger.error("[client {}] {}", serviceProxy.getProcessId(), mergedPositionMap.toString());
				logger.error("[client {}] {}", serviceProxy.getProcessId(), mergedStash);
				throw new IllegalStateException("Block is null");
				//System.exit(-1);
			} else {
				oldContent = block.getContent();
			}
		} else if (op == Operation.WRITE) {
			if (block == null) {
				block = new Block(oramContext.getBlockSize(), address, newVersionId, newContent);
				mergedStash.putBlock(block);
			} else {
				oldContent = block.getContent();
				block.setContent(newContent);
				block.setVersionId(newVersionId);
			}
		}

		return mergedStash;
	}

	private PositionMap updateLocations(int address, Operation op, int oldPathId, PositionMap mergedPositionMap,
										int newVersionId) {
		int currentBucket = mergedPositionMap.getPathAt(address);
		int[] oldPathLocations = allPaths.get(oldPathId);
		int currentBucketIndex = -1;
		if (currentBucket == ORAMUtils.DUMMY_PATH) {
			currentBucketIndex = rndGenerator.nextInt(oramContext.getTreeLevels());
			currentBucket = oldPathLocations[currentBucketIndex];
		} else {
			for (int i = 0; i < oldPathLocations.length; i++) {
				if (oldPathLocations[i] == currentBucket) {
					currentBucketIndex = i;
					break;
				}
			}
		}
		if (currentBucketIndex == -1) {
			logger.error("Current bucket {} is not in the old path {}", currentBucket, oldPathId);
			throw new IllegalStateException("Current bucket is not in the old path");
		}
		int newBucketLocation = allPaths.get(oldPathId)[
				rndGenerator.nextInt(oramContext.getTreeLevels() - currentBucketIndex) + currentBucketIndex];
		logger.debug("Block {} was moved from bucket {} to bucket {} in path {}", address, currentBucket,
				newBucketLocation, oldPathId);

		return updatePositionMap(op, mergedPositionMap, isRealAccess, address,
				newBucketLocation, newVersionId);
	}

	public boolean evict(PositionMap updatedPositionMap, Stash stash, int oldPathId) {
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		Stash remainingBlocks = populatePath(updatedPositionMap, stash, oldPathId, path);

		EncryptedStash encryptedStash = encryptionManager.encryptStash(remainingBlocks);
		EncryptedPositionMap encryptedPositionMap = encryptionManager.encryptPositionMap(updatedPositionMap);
		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(oramContext, path);

		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath);
	}

	private Stash populatePath(PositionMap positionMap, Stash stash, int oldPathId, Map<Integer, Bucket> pathToPopulate) {
		int[] oldPathLocations = allPaths.get(oldPathId);
		for (int pathLocation : oldPathLocations) {
			pathToPopulate.put(pathLocation, new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize()));
		}
		Stash remainingBlocks = new Stash(oramContext.getBlockSize());
		for (Block block : stash.getBlocks()) {
			int address = block.getAddress();
			int bucketId = positionMap.getPathAt(address);

			int pathIdForBucket = bucketToPaths.get(bucketId)[0];
			int[] pathForBucket = allPaths.get(pathIdForBucket);

			boolean isPathEmpty = false;
			for (int i = 0; i < oramContext.getTreeLevels(); i++) {
				if (oldPathLocations[i] == pathForBucket[i] && bucketId >= oldPathLocations[i]) {
					Bucket bucket = pathToPopulate.get(oldPathLocations[i]);
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
		return remainingBlocks;
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
