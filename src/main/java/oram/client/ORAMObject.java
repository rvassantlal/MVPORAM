package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.metadata.BlockMetadata;
import oram.client.metadata.BlockMetadataManager;
import oram.client.structure.*;
import oram.messages.EvictionORAMMessage;
import oram.messages.GetDebugMessage;
import oram.messages.ORAMMessage;
import oram.messages.StashPathORAMMessage;
import oram.security.EncryptionManager;
import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;
import oram.utils.*;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
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
	private OngoingAccessContext ongoingAccessContext;
	protected long globalDelayRemoteInvocation;
	protected StringBuilder debugInfoBuilder;
	private final Set<Block> blocksReceivedInStashes;
	private final Set<Block> blocksReceivedInPaths;
	protected int latestEvictionSequenceNumber;
	private final BlockMetadataManager blockMetadataManager;
	private final EvictionMap evictionMap;
	private final Map<Integer, Map<Integer, ArrayList<Pair<Block, Integer>>>> localTreeHistory = new HashMap<>();
	private final Map<Integer, Map<Integer, Stash>> localStashesHistory = new HashMap<>();
	private final Map<Integer, Integer> accessedPathsHistory = new HashMap<>();

	public ORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
					  EncryptionManager encryptionManager) {
		this.serviceProxy = serviceProxy;
		this.oramId = oramId;
		this.oramContext = oramContext;
		this.encryptionManager = encryptionManager;
		this.blockMetadataManager = new BlockMetadataManager();
		this.rndGenerator = new SecureRandom("oram".getBytes());
		this.blocksReceivedInStashes = new HashSet<>();
		this.blocksReceivedInPaths = new HashSet<>();
		this.evictionMap = new EvictionMap();
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
		ongoingAccessContext = new OngoingAccessContext(address, op, newContent);

		//Reading position maps
		long start, end, delay;
		start = System.nanoTime();
		PositionMaps oldPositionMaps = getPositionMaps();
		if (oldPositionMaps == null) {
			logger.error("Position map of oram {} is null", oramId);
			return null;
		}
		ongoingAccessContext.setNewVersionId(oldPositionMaps.getNewVersionId());
		ongoingAccessContext.setOldPositionMaps(oldPositionMaps);
		debugInfoBuilder.append("New version id: ").append(oldPositionMaps.getNewVersionId()).append("\n");
		debugInfoBuilder.append("Outstanding versions: ").append(Arrays.toString(oldPositionMaps.getOutstandingVersions())).append("\n");
		measurementLogger.info("M-receivedPM: {}", oldPositionMaps.getPositionMaps().size());

		//Merging position maps
		PositionMap mergedPositionMap = mergePositionMaps(oldPositionMaps);
		if (mergedPositionMap == null) {
			logger.error("Failed to merge position maps of oram {}", oramId);
			return null;
		}
		ongoingAccessContext.setMergedPositionMap(mergedPositionMap);

		//Merging metadata
		oldPositionMaps.getAllOutstandingVersions().put(oldPositionMaps.getNewVersionId(), oldPositionMaps.getOutstandingVersions());
		blockMetadataManager.processMetadata(oldPositionMaps.getEvictionMap(),
				oldPositionMaps.getAllOutstandingVersions(), oldPositionMaps.getNewVersionId(), debugInfoBuilder);

		end = System.nanoTime();
		delay = end - start;
		measurementLogger.info("M-map: {}", delay);


		//Translating address into a path using the merged position map
		int pathId = getPathId(mergedPositionMap, address);
		accessedPathsHistory.put(ongoingAccessContext.getNewVersionId(), pathId);

		debugInfoBuilder.append("Path id: ").append(pathId).append("\n");
		ongoingAccessContext.setAccessedPathId(pathId);

		if (serviceProxy.getProcessId() == 100 || serviceProxy.getProcessId() == 100200) {
		}
		getORAMSnapshot();

		start = System.nanoTime();
		Stash mergedStash = getPS();
		end = System.nanoTime();
		delay = end - start;
		ongoingAccessContext.setMergedStash(mergedStash);
		measurementLogger.info("M-ps: {}", delay);

		accessBlockAndPerformOperation(address, op, oldPositionMaps.getNewVersionId(), newContent, mergedStash);

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
		if (serviceProxy.getProcessId() == 100200) {
			logger.info("[Client {}] {}\n\n\n\n", serviceProxy.getProcessId(), debugInfoBuilder.toString());
		}
		return ongoingAccessContext.getOldContent();
	}

	protected void reset() {
		globalDelayRemoteInvocation = 0;
		ongoingAccessContext = null;
		evictionMap.reset();
	}

	public int getPathId(PositionMap mergedPositionMap, int address) {
		int bucketId = mergedPositionMap.getLocationOf(address);

		ongoingAccessContext.setAccessedAddressBucket(bucketId);
		logger.debug("Getting pathId {} for address {} and version {}", bucketId, address,
				mergedPositionMap.getLocationVersionOf(address));
		if (bucketId == ORAMUtils.DUMMY_LOCATION) {
			ongoingAccessContext.setIsRealAccess(false);
			return generateRandomPathId();
		}
		int[] possiblePaths = bucketToPaths.get(bucketId);
		return possiblePaths[rndGenerator.nextInt(possiblePaths.length)];
	}

	public Stash getPS() {
		int pathId = ongoingAccessContext.getAccessedPathId();
		PositionMap mergedPositionMap = ongoingAccessContext.getMergedPositionMap();

		StashesAndPaths stashesAndPaths = getStashesAndPaths(pathId);
		if (stashesAndPaths == null) {
			logger.error("States and paths of oram {} are null", oramId);
			return null;
		}

		return mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths(), mergedPositionMap);
	}

	private void accessBlockAndPerformOperation(int address, Operation op, int newVersion, byte[] newContent,
												Stash stash) {
		Block block = stash.getBlock(address);

		if (ongoingAccessContext.isRealAccess() && op == Operation.READ) {
			if (block == null) {
				String debugInfo = buildDebugInfo(address, stash);
				logger.error("[client {} - Error] {}", serviceProxy.getProcessId(), debugInfo);
				getORAMSnapshot();
				System.exit(-1);
				throw new IllegalStateException("Block is null");
			} else {
				ongoingAccessContext.setOldContent(block.getContent());
			}
		} else if (op == Operation.WRITE) {
			if (block == null) {
				block = new Block(oramContext.getBlockSize(), address, newVersion, newContent);
				stash.putBlock(block);
			} else {
				ongoingAccessContext.setOldContent(block.getContent());
				block.setContent(newContent);
				block.setContentVersion(newVersion);
				block.setLocationVersion(newVersion);
			}
		}
	}

	private String buildDebugInfo(int address, Stash mergedStash) {
		PositionMap mergedPositionMap = ongoingAccessContext.getMergedPositionMap();
		StringBuilder errorMessageBuilder = new StringBuilder();
		errorMessageBuilder.append("Reading address ").append(address).append(" from bucket ")
				.append(ongoingAccessContext.getAccessedAddressBucket())
				.append(" (path ").append(ongoingAccessContext.getAccessedPathId()).append(")\n");
		errorMessageBuilder.append("New version id: ").append(ongoingAccessContext.getNewVersionId()).append("\n");
		errorMessageBuilder.append("Outstanding versions: ").append(Arrays.toString(ongoingAccessContext.getOldPositionMaps().getOutstandingVersions()));
		PositionMaps oldPositionMaps = ongoingAccessContext.getOldPositionMaps();
		errorMessageBuilder.append("Old position maps:\n");
		for (Map.Entry<Integer, PositionMap> entry : oldPositionMaps.getPositionMaps().entrySet()) {
			PositionMap currentPM = entry.getValue();
			int modifiedBlockAddress = currentPM.getAddress()[0];
			int modifiedBlockLocation = currentPM.getLocationOf(modifiedBlockAddress);
			int substitutedBlockAddress = currentPM.getAddress()[1];
			int substitutedBlockLocation = currentPM.getLocationOf(substitutedBlockAddress);
			errorMessageBuilder.append("\t").append(entry.getKey()).append(" -> modified: ")
					.append(modifiedBlockAddress).append(" ")
					.append(modifiedBlockLocation).append(" ")
					.append(currentPM.getContentVersionOf(modifiedBlockAddress)).append(" ")
					.append(currentPM.getLocationVersionOf(modifiedBlockAddress))
					.append("\t\tsubstituted: ")
					.append(substitutedBlockAddress).append(" ")
					.append(substitutedBlockLocation).append(" ")
					.append(currentPM.getContentVersionOf(substitutedBlockAddress)).append(" ")
					.append(currentPM.getLocationVersionOf(substitutedBlockAddress)).append("\n");
		}

		errorMessageBuilder.append("Old eviction maps:\n");
		for (Map.Entry<Integer, EvictionMap> entry : oldPositionMaps.getEvictionMap().entrySet()) {
			errorMessageBuilder.append("\t").append(entry.getKey()).append(":\n")
					.append("\t\tMoved to path -> ").append(entry.getValue().getBlocksMovedToPath()).append("\n")
					.append("\t\tMoved from path -> ").append(entry.getValue().getBlocksRemovedFromPath()).append("\n");
		}

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

		int currentBucket = positionMap.getLocationOf(address);
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
		int[] possiblePaths = bucketToPaths.get(newBucketLocation);
		int pathToUse = possiblePaths[rndGenerator.nextInt(possiblePaths.length)];
		int[] pathToUseLocations = allPaths.get(pathToUse);
		int pathToUseIndex = -1;
		for (int i = 0; i < pathToUseLocations.length; i++) {
			if (pathToUseLocations[i] == newBucketLocation) {
				pathToUseIndex = i;
				break;
			}
		}

		if (pathToUseIndex == -1) {
			logger.error("New bucket {} is not in the path {}", newBucketLocation, pathToUse);
			throw new IllegalStateException("New bucket is not in the path");
		}

		int substitutionNewBucketLocation = pathToUseLocations[rndGenerator.nextInt(pathToUseIndex + 1)];
		//int treeSize = ORAMUtils.computeNumberOfNodes(oramContext.getTreeHeight());
		//int substitutionNewBucketLocation = rndGenerator.nextInt(treeSize - newBucketLocation) + newBucketLocation;
		ongoingAccessContext.setSubstitutedBlockNewBucketLocation(substitutionNewBucketLocation);
	}

	public boolean evict() {
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		Stash remainingBlocks = new Stash(oramContext.getBlockSize());

		populatePath(path, remainingBlocks);

		for (Block block : remainingBlocks.getBlocks()) {
			if (!blocksReceivedInStashes.contains(block) && !blocksReceivedInPaths.contains(block)) {
				evictionMap.blockRemovedFromPath(block, ORAMUtils.DUMMY_LOCATION);
			}
		}

		if (serviceProxy.getProcessId() == 100) {
			logger.info("[Client {}] Remaining stash (V: {}): \n\t{}\n", serviceProxy.getProcessId(),
					ongoingAccessContext.getNewVersionId(), remainingBlocks);
		}

		PositionMap updatedPositionMap = ongoingAccessContext.getUpdatedPositionMap();

		EncryptedStash encryptedStash = encryptionManager.encryptStash(remainingBlocks);
		EncryptedPositionMap encryptedPositionMap = encryptionManager.encryptPositionMap(updatedPositionMap);
		debugInfoBuilder.append("Eviction map\n");
		debugInfoBuilder.append("\tmoved to path:\n");
		debugInfoBuilder.append("\t\tlocations: ").append(evictionMap.getBlocksMovedToPath().getLocations()).append("\n");
		debugInfoBuilder.append("\t\tcontent versions: ").append(evictionMap.getBlocksMovedToPath().getContentVersions()).append("\n");
		debugInfoBuilder.append("\t\tlocation versions: ").append(evictionMap.getBlocksMovedToPath().getLocationVersions()).append("\n");
		debugInfoBuilder.append("\tmoved from path:\n");
		debugInfoBuilder.append("\t\tlocations: ").append(evictionMap.getBlocksRemovedFromPath().getLocations()).append("\n");
		debugInfoBuilder.append("\t\tcontent versions: ").append(evictionMap.getBlocksRemovedFromPath().getContentVersions()).append("\n");
		debugInfoBuilder.append("\t\tlocation versions: ").append(evictionMap.getBlocksRemovedFromPath().getLocationVersions()).append("\n");
		debugInfoBuilder.append("Path:\n\t").append(path).append("\n");
		debugInfoBuilder.append("Remaining stash:\n\t").append(remainingBlocks).append("\n");

		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(oramContext, path);
		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath, evictionMap);
	}

	private void populatePath(Map<Integer, Bucket> pathToPopulate, Stash remainingBlocks) {
		Stash stash = ongoingAccessContext.getMergedStash();

		PositionMap positionMap = ongoingAccessContext.getMergedPositionMap();
		int[] accessedPathLocations = allPaths.get(ongoingAccessContext.getAccessedPathId());
		int accessedBlockAddress = ongoingAccessContext.getAddress();
		int accessedAddressNewBucketLocation = ongoingAccessContext.getAccessedAddressNewBucketLocation();

		for (int pathLocation : accessedPathLocations) {
			pathToPopulate.put(pathLocation, new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize(), pathLocation));
		}

		boolean isSubstituted = false;

		for (Block block : stash.getBlocks()) {
			int address = block.getAddress();
			int bucketId = positionMap.getLocationOf(address);
			if (address == accessedBlockAddress) {
				bucketId = accessedAddressNewBucketLocation;
				block.setLocationVersion(ongoingAccessContext.getNewVersionId());
			} else if (bucketId == accessedAddressNewBucketLocation && !isSubstituted) {
				bucketId = ongoingAccessContext.getSubstitutedBlockNewBucketLocation();
				ongoingAccessContext.setSubstitutedBlockAddress(address);
				block.setLocationVersion(ongoingAccessContext.getNewVersionId());
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
						//If block was received in stash or is the first time accessing the address
						evictionMap.moveBlockToPath(block, accessedPathLocations[i]);
						evictionMap.blockAddedBackToPath(block, accessedPathLocations[i]);
						break;
					}
				}
			}
			if (!isPathEmpty) {
				remainingBlocks.putBlock(block);
			}
		}

		PositionMap updatedPositionMap;

		logger.debug("Block {} was moved up from bucket {} to bucket {} in path {}",
				accessedBlockAddress, positionMap.getLocationOf(accessedBlockAddress),
				accessedAddressNewBucketLocation, ongoingAccessContext.getAccessedPathId());
		if (isSubstituted) {
			logger.debug("Block {} was moved down from bucket {} to bucket {}",
					ongoingAccessContext.getSubstitutedBlockAddress(),
					positionMap.getLocationOf(ongoingAccessContext.getSubstitutedBlockAddress()),
					ongoingAccessContext.getSubstitutedBlockNewBucketLocation());

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
										Map<Integer, EncryptedBucket> encryptedPath, EvictionMap evictionMap) {
		try {
			EvictionORAMMessage request = new EvictionORAMMessage(oramId, encryptedStash, encryptedPositionMap,
					encryptedPath, evictionMap);
			byte[] serializedDataRequest = ORAMUtils.serializeRequest(ServerOperationType.EVICTION, request);

			long start, end, delay;
			start = System.nanoTime();
			boolean isSuccessful = sendRequestData(serializedDataRequest);
			end = System.nanoTime();
			delay = end - start;
			if (!isSuccessful) {
				return false;
			}

			int hash = serviceProxy.getProcessId() + ORAMUtils.computeHashCode(serializedDataRequest) * 32;
			ORAMMessage dataHashRequest = new ORAMMessage(hash);//Sending request hash as oramId (not ideal implementation)
			byte[] serializedEvictionRequest = ORAMUtils.serializeRequest(ServerOperationType.EVICTION, dataHashRequest);

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

	private Stash mergeStashesAndPaths(Map<Integer, Stash> stashes, Bucket[] paths, PositionMap mergedPositionMap) {
		blocksReceivedInStashes.clear();
		blocksReceivedInPaths.clear();

		mergeStashes(blocksReceivedInStashes, stashes);
		mergePaths(blocksReceivedInPaths, paths);

		//Remove blocks received in stashes that were also received in paths
		blocksReceivedInPaths.forEach(blocksReceivedInStashes::remove);

		Stash mergedStash = new Stash(oramContext.getBlockSize());
		blocksReceivedInStashes.forEach(mergedStash::putBlock);
		blocksReceivedInPaths.forEach(mergedStash::putBlock);

		debugInfoBuilder.append("Merged stash:\n\t").append(mergedStash).append("\n");

		return mergedStash;
	}

	private void mergePaths(Set<Block> recentBlocks, Bucket[] paths) {
		PositionMap mergedPositionMap = ongoingAccessContext.getMergedPositionMap();
		int newVersion = ongoingAccessContext.getNewVersionId();

		for (Bucket bucket : paths) {
			if (bucket == null)
				continue;
			for (Block block : bucket.readBucket()) {
				if (block == null)
					continue;
				int address = block.getAddress();
				int contentVersion = block.getContentVersion();
				int locationVersion = block.getLocationVersion();
				if (contentVersion > mergedPositionMap.getContentVersionOf(address)
						|| (contentVersion == mergedPositionMap.getContentVersionOf(address)
						&& locationVersion > mergedPositionMap.getLocationVersionOf(address))) {
					logger.warn("Block {} in path has a higher version than the position map ({}, {})",
							block, mergedPositionMap.getContentVersionOf(address), mergedPositionMap.getLocationVersionOf(address));
					throw new IllegalStateException("Block's version in path is higher than in PM");
				}

				if (mergedPositionMap.getContentVersionOf(address) == contentVersion
						&& mergedPositionMap.getLocationVersionOf(address) == locationVersion) {
					evictionMap.blockRemovedFromPath(block, bucket.getLocation());
					if (blockMetadataManager.isInHighestLocation(newVersion, block.getAddress(), bucket.getLocation())) {
						recentBlocks.add(block);
					}
				}
			}
		}
	}

	private void mergeStashes(Set<Block> recentBlocks, Map<Integer, Stash> stashes) {
		PositionMap mergedPositionMap = ongoingAccessContext.getMergedPositionMap();
		int newVersion = ongoingAccessContext.getNewVersionId();
		//Filter recent blocks that were not evicted
		debugInfoBuilder.append("Received stashes versions: ").append(stashes.keySet()).append("\n");
		for (Map.Entry<Integer, Stash> entry : stashes.entrySet()) {
			Stash stash = entry.getValue();
			if (stash == null) {
				logger.warn("Stash is null");
				continue;
			}

			for (Block block : stash.getBlocks()) {
				int address = block.getAddress();
				int contentVersion = block.getContentVersion();

				if (contentVersion > mergedPositionMap.getContentVersionOf(address)
						|| (contentVersion == mergedPositionMap.getContentVersionOf(address)
						&& block.getLocationVersion() > mergedPositionMap.getLocationVersionOf(address))) {
					logger.warn("Block {} in stash has a higher version than the position map ({}, {})",
							block, mergedPositionMap.getContentVersionOf(address),
							mergedPositionMap.getLocationVersionOf(address));
					throw new IllegalStateException("Block in stash has a higher version than the position map");
				}

				if (mergedPositionMap.getContentVersionOf(address) == contentVersion
						&& mergedPositionMap.getLocationVersionOf(address) == block.getLocationVersion()
						&& !blockMetadataManager.isInTree(newVersion, block.getAddress())) {
					recentBlocks.add(block);
				}
			}
		}
	}

	private StashesAndPaths getStashesAndPaths(int pathId) {
		try {
			ORAMMessage request = new StashPathORAMMessage(oramId, pathId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_STASH_AND_PATH, request);

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

	public void getORAMSnapshot() {
		try {
			ORAMMessage request = new GetDebugMessage(oramId, serviceProxy.getProcessId());
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.DEBUG, request);

			Response response = serviceProxy.invokeOrderedHashed(serializedRequest);
			if (response == null || response.getPlainData() == null) {
				return;
			}
			DebugSnapshot snapshot = encryptionManager.decryptDebugSnapshot(oramContext, response.getPlainData());
			analiseSnapshot(snapshot);
		} catch (SecretSharingException e) {
			throw new RuntimeException(e);
		}
	}

	private void analiseSnapshot(DebugSnapshot snapshot) {
		StringBuilder messageBuilder = new StringBuilder("============= Start of snapshot =============\n");
		Set<Block> mergedStashBlocksSet = new HashSet<>();
		localStashesHistory.put(ongoingAccessContext.getNewVersionId(), snapshot.getStashes());
		messageBuilder.append("Stashes:\n");
		snapshot.getStashes().keySet().stream().sorted().forEach(version -> {
			messageBuilder.append("\tStash ").append(version).append(": ").append(snapshot.getStashes().get(version)).append("\n");
		});

		mergeStashes(mergedStashBlocksSet, snapshot.getStashes());
		PositionMap positionMap = ongoingAccessContext.getMergedPositionMap();
		Map<Integer, Block> mergedStashBlocks = new HashMap<>(mergedStashBlocksSet.size());
		mergedStashBlocksSet.forEach(b -> mergedStashBlocks.put(b.getAddress(), b));

		//Group blocks by address
		Map<Integer, ArrayList<Pair<Block, Integer>>> unfilteredBlocksGroupedByAddress = new HashMap<>();
		Map<Integer, ArrayList<Pair<Block, Integer>>> blocksGroupedByAddress = localTreeHistory.computeIfAbsent(ongoingAccessContext.getNewVersionId(), k -> new HashMap<>());
		for (ArrayList<Bucket> buckets : snapshot.getTree()) {
			for (Bucket bucket : buckets) {
				for (Block block : bucket.readBucket()) {
					if (block != null) {
						int address = block.getAddress();
						int contentVersion = block.getContentVersion();
						int locationVersion = block.getLocationVersion();
						int pmContentVersion = positionMap.getContentVersionOf(address);
						int pmLocationVersion = positionMap.getLocationVersionOf(address);
						unfilteredBlocksGroupedByAddress.computeIfAbsent(address, k -> new ArrayList<>())
								.add(Pair.of(block, bucket.getLocation()));
						if (contentVersion > pmContentVersion || (contentVersion == pmContentVersion && locationVersion > pmLocationVersion)) {
							throw new IllegalStateException("Block " + block + " is in future");
						}
						if (contentVersion == pmContentVersion && locationVersion == pmLocationVersion) {
							ArrayList<Pair<Block, Integer>> pairs = blocksGroupedByAddress.computeIfAbsent(address, k -> new ArrayList<>());
							pairs.add(Pair.of(block, bucket.getLocation()));
						}
					}
				}
			}
		}

		//printing merged position map
		messageBuilder.append("Merged position map: (address, location, content version, location version)\n\t");
		for (int address = 0; address < oramContext.getTreeSize(); address++) {
			int location = positionMap.getLocationOf(address);
			if (location == ORAMUtils.DUMMY_LOCATION) {
				continue;
			}
			int contentVersion = positionMap.getContentVersionOf(address);
			int locationVersion = positionMap.getLocationVersionOf(address);
			messageBuilder.append("(").append(address).append(", ").append(location).append(", ")
					.append(contentVersion).append(", ").append(locationVersion).append(") ");
		}
		messageBuilder.append("\n");

		Set<Integer> strangeBlocks = new HashSet<>();
		Set<Integer> unknownBlocks = new HashSet<>();
		Map<Integer, BlockMetadata> metadata = blockMetadataManager.getMetadata(ongoingAccessContext.getNewVersionId());

		for (int address = 0; address < oramContext.getTreeSize(); address++) {
			int pmLocation = positionMap.getLocationOf(address);
			if (pmLocation == ORAMUtils.DUMMY_LOCATION) {
				continue;
			}
			ArrayList<Pair<Block, Integer>> unfilteredBlocksInTree = unfilteredBlocksGroupedByAddress.get(address);
			ArrayList<Pair<Block, Integer>> blocksInTree = blocksGroupedByAddress.get(address);
			Block blockInStash = mergedStashBlocks.get(address);
			if (blocksInTree == null && blockInStash == null) {
				unknownBlocks.add(address);
				continue;
			}

			int pmContentVersion = positionMap.getContentVersionOf(address);
			int pmLocationVersion = positionMap.getLocationVersionOf(address);
			/*if (metadata.get(address) == null) {
				debugInfoBuilder.append(messageBuilder);
				System.out.println(debugInfoBuilder);
			}*/
			int mBlockLocation = metadata.get(address).getHighestLocation();
			int mBlockContentVersion = metadata.get(address).getContentVersion();
			int mBlockLocationVersion = metadata.get(address).getLocationVersion();

			if ((blocksInTree != null && blockInStash != null)
					|| (blocksInTree != null && mBlockLocation == ORAMUtils.DUMMY_LOCATION)) {
				strangeBlocks.add(address);
			}

			boolean isInStash = blocksInTree == null && mBlockLocation == ORAMUtils.DUMMY_LOCATION
					&& mBlockContentVersion == pmContentVersion && mBlockLocationVersion == pmLocationVersion
					&& blockInStash.getContentVersion() == mBlockContentVersion
					&& blockInStash.getLocationVersion() == mBlockLocationVersion;
			boolean isSingleInTree = blockInStash == null
					&& blocksInTree.size() == 1 && blocksInTree.get(0).getRight() == mBlockLocation
					&& mBlockContentVersion == pmContentVersion && mBlockLocationVersion == pmLocationVersion
					&& blocksInTree.get(0).getLeft().getContentVersion() == mBlockContentVersion
					&& blocksInTree.get(0).getLeft().getLocationVersion() == mBlockLocationVersion;

			if (!isInStash && !isSingleInTree) {
				messageBuilder.append("Block ").append(address).append("\n");
				messageBuilder.append("\tVersion in PM -> ")
						.append(pmLocation).append(" ")
						.append(pmContentVersion).append(" ")
						.append(pmLocationVersion).append("\n");
				messageBuilder.append("\tUnfiltered versions in tree -> ").append(unfilteredBlocksInTree).append("\n");
				messageBuilder.append("\tVersions in tree -> ").append(blocksInTree).append("\n");
				messageBuilder.append("\tVersion in stash -> ").append(blockInStash).append("\n");
				messageBuilder.append("\tBlock location -> ").append(mBlockContentVersion).append(" ")
						.append(mBlockLocationVersion).append(" ").append(mBlockLocation)
						.append(" ").append(metadata.get(address).getHighestLocationVersion()).append("\n");
			}
		}

		messageBuilder.append("Merged stash size: ").append(mergedStashBlocks.size()).append("\n");
		messageBuilder.append("Merged stash:\n\t").append(mergedStashBlocks).append("\n");

		messageBuilder.append("Strange blocks: ").append(strangeBlocks.size()).append("\n");
		strangeBlocks.stream().sorted().forEach(address ->
				messageBuilder.append("\tBlock ").append(address).append("\n")
						.append("\t\tVersion in PM -> ").append(positionMap.getContentVersionOf(address)).append(" ")
						.append(positionMap.getLocationVersionOf(address)).append("\n")
						.append("\t\tVersions in tree -> ").append(blocksGroupedByAddress.get(address)).append("\n")
						.append("\t\tVersion in stash -> ").append(mergedStashBlocks.get(address)).append("\n")
						.append("\t\tBlock location -> ").append(metadata.get(address)).append("\n"));

		//print strange blocks
		messageBuilder.append("Unknown blocks:\n");
		unknownBlocks.stream().sorted().forEach(address -> {
			int pmLocation = positionMap.getLocationOf(address);
			int pmContentVersion = positionMap.getContentVersionOf(address);
			int pmLocationVersion = positionMap.getLocationVersionOf(address);
			ArrayList<Pair<Block, Integer>> unfilteredBlocksInTree = unfilteredBlocksGroupedByAddress.get(address);

			messageBuilder.append("\tBlock ").append(address).append("\n");
			messageBuilder.append("\t\tVersion in PM -> ").append(pmLocation).append(" ")
					.append(pmContentVersion).append(" ")
					.append(pmLocationVersion).append("\n");
			messageBuilder.append("\t\tBlock location -> ").append(metadata.get(address)).append("\n");
			messageBuilder.append("\t\tUnfiltered versions in tree -> ").append(unfilteredBlocksInTree).append("\n");
		});
		messageBuilder.append("Number of unknown blocks: ").append(unknownBlocks.size()).append("\n");
		messageBuilder.append("============= End of snapshot =============\n");
		//logger.info("[Client {}] {}\n", serviceProxy.getProcessId(), messageBuilder);
		debugInfoBuilder.append(messageBuilder);

		if (!unknownBlocks.isEmpty()) {
			String message = "[Client " + serviceProxy.getProcessId() + "] " + debugInfoBuilder
					+ "\nUnknown blocks ERROR";
			throw new IllegalStateException(message);
		}

		if (!strangeBlocks.isEmpty()) {
			String message = "[Client " + serviceProxy.getProcessId() + "] " + debugInfoBuilder
					+ "\nStrange blocks ERROR";
			throw new IllegalStateException(message);
		}
	}

	public void serializeDebugData() {
		PositionMap positionMap = ongoingAccessContext.getMergedPositionMap();
		long time = System.currentTimeMillis();
		int clientId = serviceProxy.getProcessId();
		String filename = "C:\\Users\\robin\\Desktop\\oram\\oramErrorTrace_" + time
				+ "_client_" + clientId + ".txt";
		try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(Paths.get(filename)));
			 ObjectOutput out = new ObjectOutputStream(bos)) {
			out.writeInt(oramContext.getTreeHeight());
			out.writeInt(oramContext.getBlockSize());
			out.writeInt(oramContext.getBucketSize());
			out.writeInt(ongoingAccessContext.getNewVersionId());

			//serialize position map
			for (int address = 0; address < oramContext.getTreeSize(); address++) {
				int location = positionMap.getLocationOf(address);
				int contentVersion = positionMap.getContentVersionOf(address);
				int locationVersion = positionMap.getLocationVersionOf(address);
				out.writeInt(location);
				out.writeInt(contentVersion);
				out.writeInt(locationVersion);
			}

			//serialize eviction maps
			Map<Integer, EvictionMap> evictionMapHistory = blockMetadataManager.getLocalEvictionMapHistory();
			out.writeInt(evictionMapHistory.size());
			for (Map.Entry<Integer, EvictionMap> entry : evictionMapHistory.entrySet()) {
				out.writeInt(entry.getKey());
				EvictionMap evictionMap = entry.getValue();
				int size = evictionMap.getSerializedSize();
				byte[] serializedEvictionMap = new byte[size];
				evictionMap.writeExternal(serializedEvictionMap, 0);
				out.writeInt(size);
				out.write(serializedEvictionMap);
			}

			//serialize outstanding versions
			Map<Integer, int[]> outstandingVersionsHistory = blockMetadataManager.getLocalOutstandingVersionsHistory();
			out.writeInt(outstandingVersionsHistory.size());
			for (Map.Entry<Integer, int[]> entry : outstandingVersionsHistory.entrySet()) {
				out.writeInt(entry.getKey());
				int[] outstandingVersions = entry.getValue();
				out.writeInt(outstandingVersions.length);
				for (int outstandingVersion : outstandingVersions) {
					out.writeInt(outstandingVersion);
				}
			}

			//serialize trees
			out.writeInt(localTreeHistory.size());
			for (Integer version : localTreeHistory.keySet()) {
				out.writeInt(version);

				Map<Integer, ArrayList<Pair<Block, Integer>>> blocksGroupedByAddress = localTreeHistory.get(version);
				out.writeInt(blocksGroupedByAddress.size());
				for (Integer address : blocksGroupedByAddress.keySet()) {
					out.writeInt(address);
					ArrayList<Pair<Block, Integer>> blocksAndLocations = blocksGroupedByAddress.get(address);
					out.writeInt(blocksAndLocations.size());
					for (Pair<Block, Integer> entry : blocksAndLocations) {
						Block block = entry.getLeft();
						int location = entry.getRight();
						int blockSize = block.getSerializedSize();
						byte[] serializedBlock = new byte[blockSize];
						block.writeExternal(serializedBlock, 0);
						out.writeInt(location);
						out.writeInt(blockSize);
						out.write(serializedBlock);
					}
				}
			}

			//serialize stashes
			out.writeInt(localStashesHistory.size());
			for (Integer v : localStashesHistory.keySet()) {
				Map<Integer, Stash> stashes = localStashesHistory.get(v);
				out.writeInt(v);
				out.writeInt(stashes.size());
				for (Map.Entry<Integer, Stash> entry : stashes.entrySet()) {
					int version = entry.getKey();
					Stash stash = entry.getValue();
					out.writeInt(version);
					int stashSize = stash.getSerializedSize();
					byte[] serializedStash = new byte[stashSize];
					stash.writeExternal(serializedStash, 0);
					out.writeInt(stashSize);
					out.write(serializedStash);
				}
			}

			//serialize paths
			out.writeInt(accessedPathsHistory.size());
			for (Map.Entry<Integer, Integer> entry : accessedPathsHistory.entrySet()) {
				out.writeInt(entry.getKey());
				out.writeInt(entry.getValue());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected abstract PositionMap updatePositionMap(Operation op, PositionMap mergedPositionMap, boolean isRealAccess,
													 int accessedAddress, int accessedAddressNewLocation,
													 int substitutedBlockAddress, int substitutedBlockNewLocation,
													 int newVersionId);

	protected abstract PositionMap mergePositionMaps(PositionMaps oldPositionMaps);

	protected abstract PositionMaps getPositionMaps();
}
