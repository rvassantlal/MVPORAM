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
	private Map<Integer, int[]> allPaths;
	private Map<Integer, int[]> bucketToPaths;
	private BucketToLevelMap bucketToLevelMap;
	private OngoingAccessContext ongoingAccessContext;
	protected StringBuilder debugInfoBuilder;

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
		bucketToLevelMap = new BucketToLevelMap(treeHeight);
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
		debugInfoBuilder = new StringBuilder();
		debugInfoBuilder.append("Request: ").append(op).append(" ").append(address).append("\n");
		UnboundedPath unboundedPath = new UnboundedPath(oramContext.getTreeLevels());
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

		//sb.append("Merged position map:\n").append(mergedPositionMap.toStringNonNull()).append("\n");
		//sb.append("New version id: ").append(oldPositionMaps.getNewVersionId()).append("\n");

		ongoingAccessContext.setMergedPositionMap(mergedPositionMap);
		ongoingAccessContext.setNewVersionId(oldPositionMaps.getNewVersionId());

		int pathId = getPathId(mergedPositionMap, address);

		//sb.append("Accessed path id: ").append(pathId).append("\n");
		//sb.append("Is real access: ").append(ongoingAccessContext.isRealAccess()).append("\n");

		ongoingAccessContext.setAccessedPathId(pathId);

		end = System.nanoTime();
		delay = end - start;
		measurementLogger.info("M-map: {}", delay);

		start = System.nanoTime();
		Stash mergedStash = getPS(unboundedPath);
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
		//logger.info("[Client {}] {}\n\n\n\n", serviceProxy.getProcessId(), debugInfoBuilder.toString());
		return ongoingAccessContext.getOldContent();
	}

	protected void reset() {
		globalDelayRemoteInvocation = 0;
		ongoingAccessContext = null;
	}

	public int getPathId(PositionMap mergedPositionMap, int address) {
		int bucketId = mergedPositionMap.getPathAt(address);
		//sb.append("Accessed bucket id: ").append(bucketId).append("\n");
		ongoingAccessContext.setAccessedAddressBucket(bucketId);
		logger.debug("Getting pathId {} for address {} and version {}", bucketId, address,
				mergedPositionMap.getVersionIdAt(address));
		if (bucketId == ORAMUtils.DUMMY_LOCATION) {
			ongoingAccessContext.setIsRealAccess(false);
			return generateRandomPathId();
		}
		int[] possiblePaths = bucketToPaths.get(bucketId);
		return possiblePaths[rndGenerator.nextInt(possiblePaths.length)];
	}

	public Stash getPS(UnboundedPath unboundedPath) {
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
				mergedPositionMap, unboundedPath);

		Block block = mergedStash.getBlock(accessedAddress);

		if (ongoingAccessContext.isRealAccess() && op == Operation.READ) {
			if (block == null) {
				String debugInfo = getDebugInfo(ongoingAccessContext.getAddress(), mergedPositionMap,
						stashesAndPaths, mergedStash);
				logger.error("[client {} - Error] {}", serviceProxy.getProcessId(), debugInfo);
				System.exit(-1);
				throw new IllegalStateException("Block is null");
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

		debugInfoBuilder.append("Merged stash:\n\t").append(mergedStash).append("\n");
		return mergedStash;
	}

	private String getDebugInfo(int address, PositionMap mergedPositionMap, StashesAndPaths stashesAndPaths,
								Stash mergedStash) {
		StringBuilder errorMessageBuilder = new StringBuilder();
		errorMessageBuilder.append("Reading address ").append(address).append(" from bucket ")
				.append(ongoingAccessContext.getAccessedAddressBucket())
				.append(" (path ").append(ongoingAccessContext.getAccessedPathId()).append(")\n");
		errorMessageBuilder.append("Stashes:\n");
		int counter = 1;
		for (Stash stash : stashesAndPaths.getStashes()) {
			errorMessageBuilder.append("Stash ").append(counter).append(" -> ").append(stash).append("\n");
			counter++;
		}
		counter = 1;
		errorMessageBuilder.append("Evicted blocks:\n");
		for (Stash stash : stashesAndPaths.getStashes()) {
			boolean isIn = stash.getEvictedBlocks().containsKey(address);
			errorMessageBuilder.append("Evicted blocks ").append(counter).append(" (").append(isIn).append(") -> ").append(stash.getEvictedBlocks()).append("\n");
			counter++;
		}
		counter = 1;
		errorMessageBuilder.append("Paths:\n");
		for (Bucket path : stashesAndPaths.getPaths()) {
			errorMessageBuilder.append("Path ").append(counter).append(" -> ").append(path).append("\n");
			counter++;
		}
		//errorMessageBuilder.append("Block tracker:\n").append(blockTracker.get(address)).append("\n");
		errorMessageBuilder.append("Position map:\n").append(mergedPositionMap.toStringNonNull()).append("\n");
		errorMessageBuilder.append("Merged stash:\n").append(mergedStash).append("\n");
		return errorMessageBuilder.toString();
	}

	private void computeNewLocations() {
		if (!ongoingAccessContext.isRealAccess() && ongoingAccessContext.getOperation() == Operation.READ) {
			ongoingAccessContext.setAccessedAddressNewBucketLocation(ORAMUtils.DUMMY_LOCATION);
			ongoingAccessContext.setSubstitutedBlockNewBucketLocation(ORAMUtils.DUMMY_LOCATION);
			return;
		}
		int address = ongoingAccessContext.getAddress();
		PositionMap positionMap = ongoingAccessContext.getMergedPositionMap();
		int accessedPathId = ongoingAccessContext.getAccessedPathId();

		int currentBucket = positionMap.getPathAt(address);
		int[] accessedPathLocations = allPaths.get(accessedPathId);

		int currentBucketIndex = -1;
		if (currentBucket == ORAMUtils.DUMMY_LOCATION) {
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

		//Precompute the new location of the substituted block. It might not be used.
		int treeSize = ORAMUtils.computeNumberOfNodes(oramContext.getTreeHeight());
		int substitutionNewBucketLocation = rndGenerator.nextInt(treeSize - newBucketLocation) + newBucketLocation;
		ongoingAccessContext.setSubstitutedBlockNewBucketLocation(substitutionNewBucketLocation);
	}

	public boolean evict() {
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		Stash remainingBlocks = new Stash(oramContext.getBlockSize());

		populatePath(path, remainingBlocks);
		if (remainingBlocks.getBlocks().size() == 50) {
			System.exit(0);
		}
		//sb.append("Evicted position map:\n").append(ongoingAccessContext.getMergedPositionMap());
		debugInfoBuilder.append("Remaining blocks:\n\t").append(remainingBlocks).append("\n");

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
				bucketId = accessedAddressNewBucketLocation;
			} else if (bucketId == accessedAddressNewBucketLocation && !isSubstituted) {
				bucketId = ongoingAccessContext.getSubstitutedBlockNewBucketLocation();
				ongoingAccessContext.setSubstitutedBlockAddress(address);
				isSubstituted = true;
			}

			int pathIdForBucket = bucketToPaths.get(bucketId)[0];
			int[] pathForBucket = allPaths.get(pathIdForBucket);

			boolean isPathEmpty = false;
			for (int i = 0; i < oramContext.getTreeLevels(); i++) {
				if (accessedPathLocations[i] == pathForBucket[i] && bucketId >= accessedPathLocations[i]) {
					Bucket bucket = pathToPopulate.get(accessedPathLocations[i]);
					if (bucket.putBlock(block)) {
						isPathEmpty = true;
						remainingBlocks.addEvictedBlock(address, block.getVersionId());
						break;
					}
				}
			}
			if (!isPathEmpty) {
				remainingBlocks.putBlock(block);
			}
		}

		debugInfoBuilder.append("Evicted blocks:\n\t").append(remainingBlocks.getEvictedBlocks().size()).append(" -> ")
				.append(remainingBlocks.getEvictedBlocks()).append("\n");

		PositionMap updatedPositionMap;

		logger.debug("Block {} was moved up from bucket {} to bucket {} in path {}",
				accessedBlockAddress, positionMap.getPathAt(accessedBlockAddress),
				accessedAddressNewBucketLocation, ongoingAccessContext.getAccessedPathId());
		debugInfoBuilder.append("Accessed block: ").append(accessedBlockAddress).append(" -> ")
				.append(accessedAddressNewBucketLocation).append("\n");
		if (isSubstituted) {
			logger.debug("Block {} was moved down from bucket {} to bucket {}",
					ongoingAccessContext.getSubstitutedBlockAddress(),
					positionMap.getPathAt(ongoingAccessContext.getSubstitutedBlockAddress()),
					ongoingAccessContext.getSubstitutedBlockNewBucketLocation());

			debugInfoBuilder.append("Substituted block: ").append(ongoingAccessContext.getSubstitutedBlockAddress()).append(" -> ")
					.append(ongoingAccessContext.getSubstitutedBlockNewBucketLocation()).append("\n");
			updatedPositionMap = updatePositionMap(ongoingAccessContext.getOperation(), positionMap,
					ongoingAccessContext.isRealAccess(),
					accessedBlockAddress, accessedAddressNewBucketLocation,
					ongoingAccessContext.getSubstitutedBlockAddress(), ongoingAccessContext.getSubstitutedBlockNewBucketLocation(),
					ongoingAccessContext.getNewVersionId());
		} else {
			updatedPositionMap = updatePositionMap(ongoingAccessContext.getOperation(), positionMap,
					ongoingAccessContext.isRealAccess(),
					accessedBlockAddress, accessedAddressNewBucketLocation,
					ORAMUtils.DUMMY_ADDRESS, ORAMUtils.DUMMY_LOCATION,
					ongoingAccessContext.getNewVersionId());
		}
		debugInfoBuilder.append("Updated position map:\n").append(updatedPositionMap);

		ongoingAccessContext.setUpdatedPositionMap(updatedPositionMap);

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

	private Stash mergeStashesAndPaths(Stash[] stashes, Bucket[] paths, PositionMap mergedPositionMap,
									   UnboundedPath unboundedPath) {
		Map<Integer, Block> recentBlocks = new HashMap<>();

		mergeStashes(recentBlocks, stashes, mergedPositionMap, unboundedPath);
		mergePaths(recentBlocks, paths, mergedPositionMap, unboundedPath);

		Stash mergedStash = new Stash(oramContext.getBlockSize());
		int counter = 0;
		for (Block recentBlock : recentBlocks.values()) {
			mergedStash.putBlock(recentBlock);
			if (recentBlock.getVersionId() != mergedPositionMap.getBlockModificationVersionAt(recentBlock.getAddress())) {
				counter++;
			}
		}
		debugInfoBuilder.append("Blocks with different version id: ").append(counter).append("\n");

		return mergedStash;
	}

	private void mergePaths(Map<Integer, Block> recentBlocks, Bucket[] paths,
							PositionMap mergedPositionMap, UnboundedPath unboundedPath) {
		int counter = 1;
		for (Bucket bucket : paths) {
			//sb.append("Bucket ").append(counter++).append(" -> ").append(bucket).append("\n");
			if (bucket == null)
				continue;
			for (Block block : bucket.readBucket()) {
				if (block == null)
					continue;
				int blockAddress = block.getAddress();
				int blockVersionId = block.getVersionId();
				Block storedBlock = recentBlocks.get(blockAddress);
				if (storedBlock == null || storedBlock.getVersionId() < blockVersionId) {
					recentBlocks.put(blockAddress, block);
					int level = bucketToLevelMap.toLevel(mergedPositionMap.getPathAt(blockAddress));
					unboundedPath.addBlock(level, block);
				}
			}
		}
	}

	private void mergeStashes(Map<Integer, Block> recentBlocks, Stash[] stashes,
							  PositionMap mergedPositionMap, UnboundedPath unboundedPath) {
		//Combine all evictedBlocks from all stashes
		Map<Integer, Integer> evictedBlocks = new HashMap<>();
		int counter = 1;
		for (Stash stash : stashes) {
			debugInfoBuilder.append("Stash ").append(counter++).append(" -> ").append(stash).append("\n");
			if (stash == null) {
				continue;
			}
			for (Map.Entry<Integer, Integer> entry : stash.getEvictedBlocks().entrySet()) {
				if (mergedPositionMap.getVersionIdAt(entry.getKey()) > entry.getValue()) {//Ignoring old evicted blocks
					continue;
				}
				Integer version = evictedBlocks.get(entry.getKey());
				if (version == null || version < entry.getValue()) {
					evictedBlocks.put(entry.getKey(), entry.getValue());
				}
			}
		}

		//Filter recent blocks that were not evicted
		for (Stash stash : stashes) {
			if (stash == null) {
				logger.warn("Stash is null");
				continue;
			}

			for (Block block : stash.getBlocks()) {
				int blockAddress = block.getAddress();
				int blockVersionId = block.getVersionId();

				Block storedBlock = recentBlocks.get(blockAddress);
				if (blockVersionId > evictedBlocks.getOrDefault(blockAddress, ORAMUtils.DUMMY_VERSION)
						&& (storedBlock == null || blockVersionId > storedBlock.getVersionId())) {
					recentBlocks.put(blockAddress, block);
					int level = bucketToLevelMap.toLevel(mergedPositionMap.getPathAt(blockAddress));
					unboundedPath.addBlock(level, block);
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
	protected abstract PositionMap updatePositionMap(Operation op, PositionMap mergedPositionMap, boolean isRealAccess,
													 int accessedAddress, int accessedAddressNewLocation,
													 int substitutedBlockAddress, int substitutedBlockNewLocation,
													 int newVersionId);

	protected abstract PositionMap mergePositionMaps(PositionMaps oldPositionMaps);

	protected abstract PositionMaps getPositionMaps();
}
