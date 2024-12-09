package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.metadata.OutstandingGraph;
import oram.client.structure.*;
import oram.messages.EvictionORAMMessage;
import oram.messages.GetDebugMessage;
import oram.messages.ORAMMessage;
import oram.messages.StashPathORAMMessage;
import oram.security.EncryptionManager;
import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPathMap;
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
	private final Set<Block> blocksReceivedInStashes;
	private final Set<Block> blocksReceivedInPaths;
	private final Map<Integer, Map<Integer, ArrayList<Pair<Block, Integer>>>> localTreeHistory = new HashMap<>();
	private final Map<Integer, Map<Integer, Stash>> localStashesHistory = new HashMap<>();
	private final Map<Integer, Set<Integer>> unknownBlocksHistory = new HashMap<>();
	protected final Map<Integer, PathMap> localPathMapHistory = new HashMap<>();
	private final Map<Integer, Integer> accessedPathsHistory = new HashMap<>();
	private final Set<Integer> versionHistory = new HashSet<>();
	protected final OutstandingGraph outstandingGraph;
	protected StringBuilder debugInformation;

	public ORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
					  EncryptionManager encryptionManager) {
		this.serviceProxy = serviceProxy;
		this.oramId = oramId;
		this.oramContext = oramContext;
		this.encryptionManager = encryptionManager;
		this.rndGenerator = new SecureRandom("oram".getBytes());
		this.blocksReceivedInStashes = new HashSet<>();
		this.blocksReceivedInPaths = new HashSet<>();
		this.outstandingGraph = new OutstandingGraph();
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
		debugInformation = new StringBuilder();
		debugInformation.append("Request: ").append(op).append(" ").append(address).append("\n");

		ongoingAccessContext = new OngoingAccessContext(address, op, newContent);

		//Reading path maps and obtaining a sequence/version number
		long start, end, delay;
		start = System.nanoTime();
		PositionMaps oldPositionMaps = getPositionMaps();
		if (oldPositionMaps == null) {
			logger.error("Position map of oram {} is null", oramId);
			return null;
		}
		int opSequence = oldPositionMaps.getNewVersionId();
		ongoingAccessContext.setNewVersionId(opSequence);
		ongoingAccessContext.setOldPositionMaps(oldPositionMaps);

		//Merging path maps to build tree map
		PositionMap positionMap = consolidatePathMaps(oldPositionMaps);
		if (positionMap == null) {
			logger.error("Failed to merge position maps of oram {}", oramId);
			return null;
		}
		end = System.nanoTime();
		delay = end - start;
		versionHistory.add(opSequence);
		ongoingAccessContext.setMergedPositionMap(positionMap);
		measurementLogger.info("M-receivedPM: {}", oldPositionMaps.getPathMaps().size());
		measurementLogger.info("M-map: {}", delay);
		debugInformation.append("OP sequence: ").append(opSequence).append("\n");

		//Translating address into bucket id
		int bucketId = positionMap.getLocationOf(address);
		ongoingAccessContext.setAccessedAddressBucket(bucketId);

		//Extending bucket id to a path that include that bucket
		int pathId = getPathId(bucketId);
		ongoingAccessContext.setAccessedPathId(pathId);

		accessedPathsHistory.put(ongoingAccessContext.getNewVersionId(), pathId);

		logger.debug("Getting bucket {} (path {}) for address {} (WV: {}, AV: {})", bucketId, pathId, address,
				positionMap.getWriteVersionOf(address), positionMap.getAccessVersionOf(address));
		debugInformation.append("Retrieving bucket ").append(bucketId)
						.append(" (path ").append(pathId).append(") ")
						.append(" (WV: ").append(positionMap.getWriteVersionOf(address))
						.append(", AV: ").append(positionMap.getAccessVersionOf(address))
						.append(")\n");
		getORAMSnapshot();

		start = System.nanoTime();
		StashesAndPaths stashesAndPaths = getStashesAndPaths(pathId);
		if (stashesAndPaths == null) {
			logger.error("States and paths of oram {} are null", oramId);
			return null;
		}

		Stash mergedStash = mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths(), positionMap);
		end = System.nanoTime();
		delay = end - start;
		ongoingAccessContext.setMergedStash(mergedStash);
		measurementLogger.info("M-ps: {}", delay);

		accessBlockAndPerformOperation(address, op, opSequence, newContent, mergedStash);

		start = System.nanoTime();
		boolean isEvicted = evict(pathId, address, mergedStash, positionMap);
		end = System.nanoTime();
		delay = end - start;
		measurementLogger.info("M-eviction: {}", delay);
		measurementLogger.info("M-serviceCall: {}", globalDelayRemoteInvocation);
		if (!isEvicted) {
			logger.error("Failed to do eviction on oram {}", oramId);
		}

		logger.info("[Client {}] {}", serviceProxy.getProcessId(), debugInformation);
		return ongoingAccessContext.getOldContent();
	}

	public int getPathId(int bucketId) {
		if (bucketId == ORAMUtils.DUMMY_LOCATION) {
			ongoingAccessContext.setIsRealAccess(false);
			return generateRandomPathId();
		}
		int[] possiblePaths = bucketToPaths.get(bucketId);
		return possiblePaths[rndGenerator.nextInt(possiblePaths.length)];
	}

	private int generateRandomPathId() {
		return rndGenerator.nextInt(1 << oramContext.getTreeHeight()); //2^height
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

	private Stash mergeStashesAndPaths(Map<Integer, Stash> stashes, Bucket[] paths, PositionMap positionMap) {
		blocksReceivedInStashes.clear();
		blocksReceivedInPaths.clear();

		mergeStashes(blocksReceivedInStashes, stashes, positionMap);
		mergePaths(blocksReceivedInPaths, paths, positionMap);

		for (Block blocksReceivedInStash : blocksReceivedInStashes) {
			if (blocksReceivedInPaths.contains(blocksReceivedInStash)) {
				throw new IllegalStateException("Received block " + blocksReceivedInStash + " in stash and path");
			}
		}

		Stash mergedStash = new Stash(oramContext.getBlockSize());
		blocksReceivedInStashes.forEach(mergedStash::putBlock);
		blocksReceivedInPaths.forEach(mergedStash::putBlock);

		return mergedStash;
	}

	private void mergeStashes(Set<Block> recentBlocks, Map<Integer, Stash> stashes, PositionMap positionMap) {
		//Keep blocks that are supposed to be in stash according to positionMap and ignore rest
		for (Map.Entry<Integer, Stash> stashEntry : stashes.entrySet()) {
			Stash stash = stashEntry.getValue();
			if (stash == null) {
				logger.warn("Stash is null");
				continue;
			}

			for (Map.Entry<Integer, Block> blockEntry : stash.getBlocks().entrySet()) {
				int address = blockEntry.getKey();
				Block block =  blockEntry.getValue();
				int writeVersion = block.getWriteVersion();
				int accessVersion = block.getAccessVersion();
				int positionMapLocation = positionMap.getLocationOf(address);
				int positionMapWriteVersion = positionMap.getWriteVersionOf(address);
				int positionMapAccessVersion = positionMap.getAccessVersionOf(address);
				if (writeVersion > positionMapWriteVersion
						|| (writeVersion == positionMapWriteVersion && accessVersion > positionMapAccessVersion)) {
					logger.warn("Block {} in stash has a higher version than the tree map (WV: {}, AV: {})",
							block, positionMapWriteVersion, positionMapAccessVersion);
					throw new IllegalStateException("Block in stash has a higher version than the tree map");
				}
				if (positionMapLocation == ORAMUtils.DUMMY_LOCATION && positionMapWriteVersion == writeVersion
						&& positionMapAccessVersion == accessVersion) {
					recentBlocks.add(block);
				}
			}
		}
	}

	private void mergePaths(Set<Block> recentBlocks, Bucket[] paths, PositionMap positionMap) {
		for (Bucket bucket : paths) {
			if (bucket == null)
				continue;
			for (Block block : bucket.readBucket()) {
				if (block == null)
					continue;
				int address = block.getAddress();
				int writeVersion = block.getWriteVersion();
				int accessVersion = block.getAccessVersion();
				int positionMapLocation = positionMap.getLocationOf(address);
				int positionMapWriteVersion = positionMap.getWriteVersionOf(address);
				int positionMapAccessVersion = positionMap.getAccessVersionOf(address);
				if (writeVersion > positionMapWriteVersion
						|| (writeVersion == positionMapWriteVersion && accessVersion > positionMapAccessVersion)) {
					logger.warn("Block {} in path has a higher version than the tree map (WV: {}, AV: {})",
							block, writeVersion, recentBlocks);
					throw new IllegalStateException("Block's version in path is higher than in tree map");
				}

				if (positionMapLocation == bucket.getLocation() && positionMapWriteVersion == writeVersion
						&& positionMapAccessVersion == accessVersion) {
					recentBlocks.add(block);
				}
			}
		}
	}

	private void accessBlockAndPerformOperation(int address, Operation op, int newVersion, byte[] newContent,
												Stash stash) {
		Block block = stash.getBlock(address);

		if (ongoingAccessContext.isRealAccess() && op == Operation.READ) {
			if (block == null) {
				String debugInfo = buildDebugInfo(address, stash);
				logger.error("[Client {} - Error] {}", serviceProxy.getProcessId(), debugInfo);
				logger.error("[Client {}] {}", serviceProxy.getProcessId(), debugInformation);
				System.exit(-1);
				throw new IllegalStateException("Block is null");
			}
			ongoingAccessContext.setOldContent(block.getContent());
			block.setAccessVersion(newVersion);
		} else if (op == Operation.WRITE) {
			if (block == null) {
				block = new Block(oramContext.getBlockSize(), address, newVersion, newContent);
				stash.putBlock(block);
			} else {
				ongoingAccessContext.setOldContent(block.getContent());
				block.setContent(newContent);
				block.setWriteVersion(newVersion);
				block.setAccessVersion(newVersion);
			}
		}
	}

	public boolean evict(int pathId, int accessedAddress, Stash stash, PositionMap positionMap) {
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		int pathCapacity = oramContext.getTreeLevels() * oramContext.getBucketSize();
		PathMap pathMap = new PathMap(pathCapacity);

		populatePath(pathId, accessedAddress, stash, positionMap, path, pathMap);

		ongoingAccessContext.setPathMap(pathMap);

		for (Block block : stash.getBlocks().values()) {
			pathMap.setLocation(block.getAddress(), ORAMUtils.DUMMY_LOCATION, block.getWriteVersion(),
					block.getAccessVersion());
		}

		//logger.info("[Client {}] Remaining stash (V: {}): \n\t{}\n", serviceProxy.getProcessId(),
		//		ongoingAccessContext.getNewVersionId(), stash);
		debugInformation.append("Remaining stash:\n\t").append(stash).append("\n");
		debugInformation.append("Path Map: (address, location, write version, access version)\n");
		for (int storedAddress : pathMap.getStoredAddresses()) {
			debugInformation.append("\t(").append(storedAddress)
					.append(", ").append(pathMap.getLocationOf(storedAddress))
					.append(", ").append(pathMap.getWriteVersionOf(storedAddress))
					.append(", ").append(pathMap.getAccessVersionOf(storedAddress)).append(")\n");
		}

		EncryptedStash encryptedStash = encryptionManager.encryptStash(stash);
		EncryptedPathMap encryptedPositionMap = encryptionManager.encryptPathMap(pathMap);

		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(oramContext, path);
		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath);
	}

	private void populatePath(int pathId, int accessedAddress, Stash stash, PositionMap positionMap,
							  Map<Integer, Bucket> pathToPopulate, PathMap pathMap) {
		int[] accessedPathLocations = allPaths.get(pathId);
		Set<Integer> bucketWithAvailableCapacity = new HashSet<>(accessedPathLocations.length);
		for (int pathLocation : accessedPathLocations) {
			bucketWithAvailableCapacity.add(pathLocation);
			pathToPopulate.put(pathLocation, new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize(), pathLocation));
		}

		//Move accessed block to the root bucket
		Block accessedBlock = stash.getAndRemoveBlock(accessedAddress);
		if (accessedBlock != null) {
			pathToPopulate.get(0).putBlock(accessedBlock);
			pathMap.setLocation(accessedAddress, 0, accessedBlock.getWriteVersion(), accessedBlock.getAccessVersion());
			if (pathToPopulate.get(0).isFull()) {
				bucketWithAvailableCapacity.remove(0);
			}
		}

		Set<Integer> blocksToEvict = new HashSet<>(stash.getBlocks().keySet());

		for (int address : blocksToEvict) {
			if (bucketWithAvailableCapacity.isEmpty()) {
				break;
			}
			Block block = stash.getAndRemoveBlock(address);
			int bucketId = positionMap.getLocationOf(address);

			//if it was not allocated to root bucket or the root bucket does not have space
			if (bucketId != 0 || !bucketWithAvailableCapacity.contains(0)) {
				bucketId = bucketWithAvailableCapacity.iterator().next();
			}
			Bucket bucket = pathToPopulate.get(bucketId);
			bucket.putBlock(block);
			if (bucket.isFull()) {
				bucketWithAvailableCapacity.remove(bucketId);
			}
			pathMap.setLocation(address, bucketId, block.getWriteVersion(), block.getAccessVersion());
		}
	}

	private boolean sendEvictionRequest(EncryptedStash encryptedStash, EncryptedPathMap encryptedPathMap,
										Map<Integer, EncryptedBucket> encryptedPath) {
		try {
			EvictionORAMMessage request = new EvictionORAMMessage(oramId, encryptedStash, encryptedPathMap, encryptedPath);
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

	private String buildDebugInfo(int address, Stash mergedStash) {
		PositionMap mergedPositionMap = ongoingAccessContext.getMergedPositionMap();
		StringBuilder errorMessageBuilder = new StringBuilder();
		errorMessageBuilder.append("Reading address ").append(address).append(" from bucket ")
				.append(ongoingAccessContext.getAccessedAddressBucket())
				.append(" (path ").append(ongoingAccessContext.getAccessedPathId()).append(")\n");
		errorMessageBuilder.append("New version id: ").append(ongoingAccessContext.getNewVersionId()).append("\n");
		errorMessageBuilder.append("Outstanding versions: ")
				.append(Arrays.toString(ongoingAccessContext.getOldPositionMaps().getOutstandingVersions()))
				.append("\n");
		PositionMaps oldPositionMaps = ongoingAccessContext.getOldPositionMaps();
		errorMessageBuilder.append("Old position maps:\n");
		for (Map.Entry<Integer, PathMap> entry : oldPositionMaps.getPathMaps().entrySet()) {
			PathMap currentPM = entry.getValue();
			Set<Integer> storedAddresses = currentPM.getStoredAddresses();
			errorMessageBuilder.append("\t").append(entry.getKey()).append(":");
			storedAddresses.stream().sorted().forEach(a -> errorMessageBuilder.append(" (A: ")
					.append(a)
					.append(", L: ").append(currentPM.getLocationOf(a))
					.append(", WV: ").append(currentPM.getWriteVersionOf(a))
					.append(", AV: ").append(currentPM.getAccessVersionOf(a))
					.append(")"));
			errorMessageBuilder.append("\n");
		}

		errorMessageBuilder.append("Position map:\n").append(mergedPositionMap.toStringNonNull()).append("\n");

		errorMessageBuilder.append("Merged stash:\n").append(mergedStash).append("\n");
		return errorMessageBuilder.toString();
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
		int opSequence = ongoingAccessContext.getNewVersionId();
		PositionMap positionMap = ongoingAccessContext.getMergedPositionMap();

		//printing tree map
		messageBuilder.append("Tree map: (address, location, write version, access version, location update version)\n");
		for (int address = 0; address < oramContext.getTreeSize(); address++) {
			int location = positionMap.getLocationOf(address);
			int writeVersion = positionMap.getWriteVersionOf(address);
			int accessVersion = positionMap.getAccessVersionOf(address);
			int locationUpdateVersion = positionMap.getLocationUpdateVersion(address);
			if (accessVersion == ORAMUtils.DUMMY_VERSION) {
				continue;
			}
			messageBuilder.append("\t(").append(address).append(", ").append(location).append(", ")
					.append(writeVersion).append(", ").append(accessVersion).append(", ")
					.append(locationUpdateVersion).append(")\n");
		}

		//printing stashes
		Map<Integer, Stash> stashes = snapshot.getStashes();
		localStashesHistory.put(opSequence, stashes);
		messageBuilder.append("Stashes:\n");
		stashes.keySet().stream().sorted().forEach(version -> {
			messageBuilder.append("\tStash ").append(version).append(": ").append(stashes.get(version)).append("\n");
		});

		Set<Block> mergedStashBlocks = new HashSet<>();
		mergeStashes(mergedStashBlocks, stashes, positionMap);
		Stash mergedStash = new Stash(oramContext.getBlockSize());
		mergedStashBlocks.forEach(mergedStash::putBlock);
		messageBuilder.append("Merged stash:\n");
		messageBuilder.append("\t").append(mergedStash).append("\n");

		//Group blocks by address
		Map<Integer, ArrayList<Pair<Block, Integer>>> unfilteredBlocksGroupedByAddress = new HashMap<>();
		Map<Integer, ArrayList<Pair<Block, Integer>>> blocksGroupedByAddress
				= localTreeHistory.computeIfAbsent(opSequence, k -> new HashMap<>());
		for (ArrayList<Bucket> buckets : snapshot.getTree()) {
			for (Bucket bucket : buckets) {
				for (Block block : bucket.readBucket()) {
					if (block != null) {
						int address = block.getAddress();
						int writeVersion = block.getWriteVersion();
						int accessVersion = block.getAccessVersion();
						int positionMapWriteVersion = positionMap.getWriteVersionOf(address);
						int positionMapAccessVersion = positionMap.getAccessVersionOf(address);
						unfilteredBlocksGroupedByAddress.computeIfAbsent(address, k -> new ArrayList<>())
								.add(Pair.of(block, bucket.getLocation()));
						if (writeVersion > positionMapWriteVersion || (writeVersion == positionMapWriteVersion && accessVersion > positionMapAccessVersion)) {
							throw new IllegalStateException("Block " + block + " is in future");
						}
						if (writeVersion == positionMapWriteVersion && accessVersion == positionMapAccessVersion) {
							ArrayList<Pair<Block, Integer>> pairs = blocksGroupedByAddress.computeIfAbsent(address, k -> new ArrayList<>());
							pairs.add(Pair.of(block, bucket.getLocation()));
						}
					}
				}
			}
		}

		Set<Integer> unknownBlocks = new HashSet<>();
		Set<Integer> strangeBlocks = new HashSet<>();

		//printing tree
		messageBuilder.append("Tree:\n");
		for (int address = 0; address < oramContext.getTreeSize(); address++) {
			int positionMapLocation = positionMap.getLocationOf(address);
			int positionMapWriteVersion = positionMap.getWriteVersionOf(address);
			int positionMapAccessVersion = positionMap.getAccessVersionOf(address);
			if (positionMapAccessVersion == ORAMUtils.DUMMY_VERSION) {
				continue;
			}
			ArrayList<Pair<Block, Integer>> unfilteredBlocksInTree = unfilteredBlocksGroupedByAddress.get(address);
			ArrayList<Pair<Block, Integer>> blocksInTree = blocksGroupedByAddress.get(address);
			Block blockInStash = mergedStash.getBlock(address);

			if (blocksInTree == null && blockInStash == null) {
				unknownBlocks.add(address);
			}
			if (blocksInTree != null && blockInStash != null) {
				strangeBlocks.add(address);
			}

			messageBuilder.append("\tBlock ").append(address).append("\n");
			messageBuilder.append("\t\tVersion in TM -> ")
					.append("L: ").append(positionMapLocation).append(" ")
					.append("WV: ").append(positionMapWriteVersion).append(" ")
					.append("AC: ").append(positionMapAccessVersion).append("\n");
			messageBuilder.append("\t\tUnfiltered versions in tree -> ").append(unfilteredBlocksInTree).append("\n");
			messageBuilder.append("\t\tVersions in tree -> ").append(blocksInTree).append("\n");
			messageBuilder.append("\t\tVersion in stash -> ").append(blockInStash).append("\n");
		}

		messageBuilder.append("Strange blocks: ").append(strangeBlocks).append("\n");
		messageBuilder.append("Unknown blocks: ").append(unknownBlocks).append("\n");

		messageBuilder.append("============= End of snapshot =============\n");
		unknownBlocksHistory.put(opSequence, unknownBlocks);
		debugInformation.append(messageBuilder);
		if (!unknownBlocks.isEmpty()) {
			String message = "[Client " + serviceProxy.getProcessId() + "] " + debugInformation;
			logger.info(message);
			throw new IllegalStateException(">>>>>>>>>>>>>>>>>> Unknown blocks ERROR in version " + opSequence + " <<<<<<<<<<<<<<<<<<<");
		}

		/*if (!strangeBlocks.isEmpty()) {
			String message = "[Client " + serviceProxy.getProcessId() + "] " + debugInformation
					+ "\nStrange blocks ERROR";
			throw new IllegalStateException(message);
		}*/
	}

	public void serializeDebugData() {
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

			//serialize versions
			out.writeInt(serviceProxy.getProcessId());
			out.writeInt(versionHistory.size());
			for (int v : versionHistory) {
				out.writeInt(v);
			}

			//serialize path maps
			out.writeInt(localPathMapHistory.size());
			for (Map.Entry<Integer, PathMap> entry : localPathMapHistory.entrySet()) {
				int version = entry.getKey();
				PathMap pathMap = entry.getValue();
				out.writeInt(version);
				int pathMapSize = pathMap.getSerializedSize();
				byte[] serializedPathMap = new byte[pathMapSize];
				pathMap.writeExternal(serializedPathMap, 0);
				out.writeInt(pathMapSize);
				out.write(serializedPathMap);
			}

			//serialize outstanding versions
			Map<Integer, int[]> outstandingVersionsHistory = outstandingGraph.getOutstandingVersions();
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

			//serialize unknown blocks history
			out.writeInt(unknownBlocksHistory.size());
			for (Map.Entry<Integer, Set<Integer>> entry : unknownBlocksHistory.entrySet()) {
				out.writeInt(entry.getKey());
				out.writeInt(entry.getValue().size());
				for (int v : entry.getValue()) {
					out.writeInt(v);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected void reset() {
		globalDelayRemoteInvocation = 0;
		ongoingAccessContext = null;
	}

	protected abstract PositionMap consolidatePathMaps(PositionMaps oldPositionMaps);

	protected abstract PositionMaps getPositionMaps();
}
