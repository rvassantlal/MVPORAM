package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.structure.*;
import oram.messages.EvictionORAMMessage;
import oram.messages.GetPathMaps;
import oram.messages.ORAMMessage;
import oram.messages.StashPathORAMMessage;
import oram.security.EncryptionManager;
import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPathMap;
import oram.server.structure.EncryptedStash;
import oram.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.security.SecureRandom;
import java.util.*;

public class ORAMObject {
	protected final Logger logger = LoggerFactory.getLogger("oram");
	protected final Logger measurementLogger = LoggerFactory.getLogger("measurement");
	protected final ConfidentialServiceProxy serviceProxy;
	protected final int oramId;
	protected final ORAMContext oramContext;
	protected final EncryptionManager encryptionManager;
	private final SecureRandom rndGenerator;
	private Map<Integer, int[]> allPaths;
	private Map<Integer, int[]> bucketToPaths;
	private int latestAccess;
	private final Set<Integer> missingTriples;
	private final PositionMap positionMap;
	private OngoingAccessContext ongoingAccessContext;
	protected long globalDelayRemoteInvocation;

	public ORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
					  EncryptionManager encryptionManager) {
		this.serviceProxy = serviceProxy;
		this.oramId = oramId;
		this.oramContext = oramContext;
		this.encryptionManager = encryptionManager;
		this.rndGenerator = new SecureRandom("oram".getBytes());
		this.positionMap = new PositionMap(oramContext.getTreeSize());
		this.latestAccess = 0; //server stores the initial position map and stash with version 1
		this.missingTriples = new HashSet<>();
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

		ongoingAccessContext = new OngoingAccessContext();

		//Reading path maps and obtaining a sequence/version number
		long start, end, delay;
		start = System.nanoTime();
		PathMaps pathMapsHistory = getPathMaps();
		if (pathMapsHistory == null) {
			logger.error("Position map of oram {} is null", oramId);
			return null;
		}
		int opSequence = pathMapsHistory.getOperationSequence();
		ongoingAccessContext.setOperationSequence(opSequence);
		ongoingAccessContext.setPathMapsHistory(pathMapsHistory);

		//Merging path maps to build tree map
		consolidatePathMaps(pathMapsHistory);

		end = System.nanoTime();
		delay = end - start;

		measurementLogger.info("M-receivedPM: {}", pathMapsHistory.getPathMaps().size());
		measurementLogger.info("M-map: {}", delay);

		//Translating address into bucket id
		int bucketId = positionMap.getLocation(address);
		ongoingAccessContext.setAccessedAddressBucket(bucketId);

		//Extending bucket id to a path that include that bucket
		int pathId = getPathId(bucketId);
		ongoingAccessContext.setAccessedPathId(pathId);

		logger.debug("Getting bucket {} (path {}) for address {} (WV: {}, AV: {})", bucketId, pathId, address,
				positionMap.getVersion(address), positionMap.getAccess(address));

		start = System.nanoTime();
		StashesAndPaths stashesAndPaths = getStashesAndPaths(pathId);
		if (stashesAndPaths == null) {
			logger.error("States and paths of oram {} are null", oramId);
			return null;
		}

		Stash mergedStash = mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths());
		end = System.nanoTime();
		delay = end - start;

		measurementLogger.info("M-ps: {}", delay);

		byte[] oldData = accessBlockAndPerformOperation(address, op, opSequence, newContent, mergedStash);

		start = System.nanoTime();
		boolean isEvicted = evict(pathId, address, mergedStash, positionMap);
		end = System.nanoTime();
		delay = end - start;
		measurementLogger.info("M-eviction: {}", delay);
		measurementLogger.info("M-serviceCall: {}", globalDelayRemoteInvocation);
		if (!isEvicted) {
			logger.error("Failed to do eviction on oram {}", oramId);
		}

		return oldData;
	}

	private PathMaps getPathMaps() {
		try {
			ORAMMessage request = new GetPathMaps(oramId, latestAccess, missingTriples);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_POSITION_MAP, request);

			long start, end, delay;
			start = System.nanoTime();
			Response response = serviceProxy.invokeOrderedHashed(serializedRequest);
			end = System.nanoTime();
			if (response == null || response.getPlainData() == null) {
				return null;
			}

			delay = end - start;
			globalDelayRemoteInvocation += delay;
			measurementLogger.info("M-getPM: {}", delay);

			return encryptionManager.decryptPositionMaps(response.getPlainData());
		} catch (SecretSharingException e) {
			logger.error("Error while decrypting position maps", e);
			return null;
		}
	}

	private void consolidatePathMaps(PathMaps recentPathMaps) {
		Map<Integer, PathMap> pathMaps = recentPathMaps.getPathMaps();

		int maxReceivedSequenceNumber = ORAMUtils.DUMMY_VERSION;
		for (Map.Entry<Integer, PathMap> entry : pathMaps.entrySet()) {
			int updateVersion = entry.getKey();
			maxReceivedSequenceNumber = Math.max(maxReceivedSequenceNumber, updateVersion);
			missingTriples.remove(updateVersion);

			PathMap currentPathMap = entry.getValue();
			for (int updatedAddress : currentPathMap.getStoredAddresses()) {
				int pathMapLocation = currentPathMap.getLocation(updatedAddress);
				int pathMapVersion = currentPathMap.getVersion(updatedAddress);
				int pathMapAccess = currentPathMap.getAccess(updatedAddress);
				int positionMapVersion = positionMap.getVersion(updatedAddress);
				int positionMapAccess = positionMap.getAccess(updatedAddress);
				int positionMapLocationUpdateAccess = positionMap.getLocationUpdateAccess(updatedAddress);
				if (pathMapVersion > positionMapVersion ||
						(pathMapVersion == positionMapVersion && pathMapAccess > positionMapAccess)
						|| (pathMapVersion == positionMapVersion && pathMapAccess == positionMapAccess
						&& updateVersion > positionMapLocationUpdateAccess)) {
					positionMap.update(updatedAddress, pathMapLocation, pathMapVersion,
							pathMapAccess, updateVersion);
				}
			}
		}

		for (int i = latestAccess + 1; i < maxReceivedSequenceNumber; i++) {
			if (!pathMaps.containsKey(i)) {
				missingTriples.add(i);
			}
		}
		latestAccess = maxReceivedSequenceNumber;
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

	private Stash mergeStashesAndPaths(Map<Integer, Stash> stashes, Bucket[] paths) {
		Stash mergedStash = new Stash(oramContext.getBlockSize());

		mergeStashes(mergedStash, stashes);
		mergePaths(mergedStash, paths);

		return mergedStash;
	}

	private void mergeStashes(Stash mergedStash, Map<Integer, Stash> stashes) {
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
				int version = block.getVersion();
				int access = block.getAccess();
				int positionMapLocation = positionMap.getLocation(address);
				int positionMapVersion = positionMap.getVersion(address);
				int positionMapAccess = positionMap.getAccess(address);
				if (version > positionMapVersion
						|| (version == positionMapVersion && access > positionMapAccess)) {
					logger.warn("Block {} in stash has a higher version than the position map (V: {}, A: {})",
							block, positionMapVersion, positionMapAccess);
					throw new IllegalStateException("Block in stash has a higher version than the position map");
				}
				if (positionMapLocation == ORAMUtils.DUMMY_LOCATION && positionMapVersion == version
						&& positionMapAccess == access) {
					mergedStash.putBlock(block);
				}
			}
		}
	}

	private void mergePaths(Stash mergedStash, Bucket[] paths) {
		for (Bucket bucket : paths) {
			if (bucket == null)
				continue;
			for (Block block : bucket.readBucket()) {
				if (block == null)
					continue;
				int address = block.getAddress();
				int version = block.getVersion();
				int access = block.getAccess();
				int positionMapLocation = positionMap.getLocation(address);
				int positionMapVersion = positionMap.getVersion(address);
				int positionMapAccess = positionMap.getAccess(address);
				if (version > positionMapVersion
						|| (version == positionMapVersion && access > positionMapAccess)) {
					logger.warn("Block {} in path has a higher version than the position map (V: {}, A: {})",
							block, version, access);
					throw new IllegalStateException("Block's version in path is higher than in position map");
				}

				if (positionMapLocation == bucket.getLocation() && positionMapVersion == version
						&& positionMapAccess == access) {
					mergedStash.putBlock(block);
				}
			}
		}
	}

	private byte[] accessBlockAndPerformOperation(int address, Operation op, int operationSequence, byte[] newContent,
												  Stash stash) {
		Block block = stash.getBlock(address);
		byte[] oldData = null;
		if (ongoingAccessContext.isRealAccess() && op == Operation.READ) {
			if (block == null) {
				String debugInfo = buildDebugInfo(address, stash);
				logger.error("[Client {} - Error] {}", serviceProxy.getProcessId(), debugInfo);
				System.exit(-1);
				throw new IllegalStateException("Block is null");
			}
			oldData = block.getContent();
			block.setAccess(operationSequence);
		} else if (op == Operation.WRITE) {
			if (block == null) {
				block = new Block(oramContext.getBlockSize(), address, operationSequence, newContent);
				stash.putBlock(block);
			} else {
				oldData = block.getContent();
				block.setContent(newContent);
				block.setVersion(operationSequence);
				block.setAccess(operationSequence);
			}
		}
		return oldData;
	}

	public boolean evict(int pathId, int accessedAddress, Stash stash, PositionMap positionMap) {
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		int pathCapacity = oramContext.getTreeLevels() * oramContext.getBucketSize();
		PathMap pathMap = new PathMap(pathCapacity);

		populatePathAccessedAndNewBlockToRoot(pathId, accessedAddress, stash, positionMap, path, pathMap);

		for (Block block : stash.getBlocks().values()) {
			pathMap.setLocation(block.getAddress(), ORAMUtils.DUMMY_LOCATION, block.getVersion(), block.getAccess());
		}

		EncryptedStash encryptedStash = encryptionManager.encryptStash(stash);
		EncryptedPathMap encryptedPositionMap = encryptionManager.encryptPathMap(pathMap);

		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(oramContext, path);
		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath);
	}

	private void populatePathAccessedBlockToRoot(int pathId, int accessedAddress, Stash stash, PositionMap positionMap,
									 Map<Integer, Bucket> pathToPopulate, PathMap pathMap) {
		int[] accessedPathLocations = allPaths.get(pathId);
		Map<Integer, Integer> bucketToIndex = new HashMap<>(accessedPathLocations.length);
		Set<Integer> bucketWithAvailableCapacity = new HashSet<>(accessedPathLocations.length);
		for (int i = 0; i < accessedPathLocations.length; i++) {
			int pathLocation = accessedPathLocations[i];
			bucketToIndex.put(pathLocation, i);
			bucketWithAvailableCapacity.add(pathLocation);
			pathToPopulate.put(pathLocation, new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize(), pathLocation));
		}

		//Move accessed block to the root bucket
		Block accessedBlock = stash.getAndRemoveBlock(accessedAddress);
		if (accessedBlock != null) {
			int location = positionMap.getLocation(accessedAddress);
			int newLocation;
			if (location == ORAMUtils.DUMMY_LOCATION) {
				newLocation = accessedPathLocations[rndGenerator.nextInt(accessedPathLocations.length)];
			} else {
				newLocation = 0;//accessedPathLocations[Math.min(oramContext.getTreeHeight(), bucketToIndex.get(location) + 1)];
			}

			Bucket bucket = pathToPopulate.get(newLocation);
			bucket.putBlock(accessedBlock);
			pathMap.setLocation(accessedAddress, newLocation, accessedBlock.getVersion(), accessedBlock.getAccess());
			if (bucket.isFull()) {
				bucketWithAvailableCapacity.remove(newLocation);
			}
		}

		Set<Integer> blocksToEvict = new HashSet<>(stash.getBlocks().keySet());

		for (int address : blocksToEvict) {
			if (bucketWithAvailableCapacity.isEmpty()) {
				break;
			}
			Block block = stash.getAndRemoveBlock(address);
			int bucketId = positionMap.getLocation(address);

			//if it was not allocated to root bucket or the root bucket does not have space
			if (!bucketWithAvailableCapacity.contains(bucketId)) {
				bucketId = bucketWithAvailableCapacity.iterator().next();
			}
			Bucket bucket = pathToPopulate.get(bucketId);
			bucket.putBlock(block);
			if (bucket.isFull()) {
				bucketWithAvailableCapacity.remove(bucketId);
			}
			pathMap.setLocation(address, bucketId, block.getVersion(), block.getAccess());
		}
	}

	private void populatePathAccessedBlockUp(int pathId, int accessedAddress, Stash stash, PositionMap positionMap,
											 Map<Integer, Bucket> pathToPopulate, PathMap pathMap) {
		int[] accessedPathLocations = allPaths.get(pathId);
		Map<Integer, Integer> bucketToIndex = new HashMap<>(accessedPathLocations.length);
		Set<Integer> bucketWithAvailableCapacity = new HashSet<>(accessedPathLocations.length);
		for (int i = 0; i < accessedPathLocations.length; i++) {
			int pathLocation = accessedPathLocations[i];
			bucketToIndex.put(pathLocation, i);
			bucketWithAvailableCapacity.add(pathLocation);
			pathToPopulate.put(pathLocation, new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize(), pathLocation));
		}

		//Move accessed block to the root bucket
		Block accessedBlock = stash.getAndRemoveBlock(accessedAddress);
		if (accessedBlock != null) {
			int location = positionMap.getLocation(accessedAddress);
			int newLocation;
			if (location == ORAMUtils.DUMMY_LOCATION) {
				newLocation = accessedPathLocations[rndGenerator.nextInt(accessedPathLocations.length)];
			} else {
				int currentLevel = bucketToIndex.get(location);
				int newLevel = rndGenerator.nextInt(accessedPathLocations.length - currentLevel) + currentLevel;
				newLocation = accessedPathLocations[newLevel];
			}

			Bucket bucket = pathToPopulate.get(newLocation);
			bucket.putBlock(accessedBlock);
			pathMap.setLocation(accessedAddress, newLocation, accessedBlock.getVersion(), accessedBlock.getAccess());
			if (bucket.isFull()) {
				bucketWithAvailableCapacity.remove(newLocation);
			}
		}

		Set<Integer> blocksToEvict = new HashSet<>(stash.getBlocks().keySet());

		for (int address : blocksToEvict) {
			if (bucketWithAvailableCapacity.isEmpty()) {
				break;
			}
			Block block = stash.getAndRemoveBlock(address);
			int bucketId = positionMap.getLocation(address);

			//if it was not allocated to root bucket or the root bucket does not have space
			if (!bucketWithAvailableCapacity.contains(bucketId)) {
				bucketId = bucketWithAvailableCapacity.iterator().next();
			}
			Bucket bucket = pathToPopulate.get(bucketId);
			bucket.putBlock(block);
			if (bucket.isFull()) {
				bucketWithAvailableCapacity.remove(bucketId);
			}
			pathMap.setLocation(address, bucketId, block.getVersion(), block.getAccess());
		}
	}

	private void populatePathAccessedBlockLevelUp(int pathId, int accessedAddress, Stash stash, PositionMap positionMap,
							  Map<Integer, Bucket> pathToPopulate, PathMap pathMap) {
		int[] accessedPathLocations = allPaths.get(pathId);
		Map<Integer, Integer> bucketToIndex = new HashMap<>(accessedPathLocations.length);
		Set<Integer> bucketWithAvailableCapacity = new HashSet<>(accessedPathLocations.length);
		for (int i = 0; i < accessedPathLocations.length; i++) {
			int pathLocation = accessedPathLocations[i];
			bucketToIndex.put(pathLocation, i);
			bucketWithAvailableCapacity.add(pathLocation);
			pathToPopulate.put(pathLocation, new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize(), pathLocation));
		}

		//Move accessed block to the root bucket
		Block accessedBlock = stash.getAndRemoveBlock(accessedAddress);
		if (accessedBlock != null) {
			int location = positionMap.getLocation(accessedAddress);
			int newLocation;
			if (location == ORAMUtils.DUMMY_LOCATION) {
				newLocation = accessedPathLocations[rndGenerator.nextInt(accessedPathLocations.length)];
			} else {
				newLocation = accessedPathLocations[Math.min(oramContext.getTreeHeight(), bucketToIndex.get(location) + 1)];
			}

			Bucket bucket = pathToPopulate.get(newLocation);
			bucket.putBlock(accessedBlock);
			pathMap.setLocation(accessedAddress, newLocation, accessedBlock.getVersion(), accessedBlock.getAccess());
			if (bucket.isFull()) {
				bucketWithAvailableCapacity.remove(newLocation);
			}
		}

		Set<Integer> blocksToEvict = new HashSet<>(stash.getBlocks().keySet());

		for (int address : blocksToEvict) {
			if (bucketWithAvailableCapacity.isEmpty()) {
				break;
			}
			Block block = stash.getAndRemoveBlock(address);
			int bucketId = positionMap.getLocation(address);

			//if it was not allocated to root bucket or the root bucket does not have space
			if (!bucketWithAvailableCapacity.contains(bucketId)) {
				bucketId = bucketWithAvailableCapacity.iterator().next();
			}
			Bucket bucket = pathToPopulate.get(bucketId);
			bucket.putBlock(block);
			if (bucket.isFull()) {
				bucketWithAvailableCapacity.remove(bucketId);
			}
			pathMap.setLocation(address, bucketId, block.getVersion(), block.getAccess());
		}
	}

	private void populatePathAccessedAndNewBlockToRoot(int pathId, int accessedAddress, Stash stash, PositionMap positionMap,
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
			pathMap.setLocation(accessedAddress, 0, accessedBlock.getVersion(), accessedBlock.getAccess());
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
			int bucketId = positionMap.getLocation(address);

			//if it was not allocated to root bucket or the root bucket does not have space
			if (bucketId != 0 || !bucketWithAvailableCapacity.contains(0)) {
				bucketId = bucketWithAvailableCapacity.iterator().next();
			}
			Bucket bucket = pathToPopulate.get(bucketId);
			bucket.putBlock(block);
			if (bucket.isFull()) {
				bucketWithAvailableCapacity.remove(bucketId);
			}
			pathMap.setLocation(address, bucketId, block.getVersion(), block.getAccess());
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
		StringBuilder errorMessageBuilder = new StringBuilder();
		PathMaps pathMapsHistory = ongoingAccessContext.getPathMapsHistory();
		errorMessageBuilder.append("Reading address ").append(address).append(" from bucket ")
				.append(ongoingAccessContext.getAccessedAddressBucket())
				.append(" (path ").append(ongoingAccessContext.getAccessedPathId()).append(")\n");
		errorMessageBuilder.append("Operation sequence: ").append(ongoingAccessContext.getOperationSequence()).append("\n");
		errorMessageBuilder.append("Path maps history:\n");
		for (Map.Entry<Integer, PathMap> entry : pathMapsHistory.getPathMaps().entrySet()) {
			PathMap currentPM = entry.getValue();
			Set<Integer> storedAddresses = currentPM.getStoredAddresses();
			errorMessageBuilder.append("\t").append(entry.getKey()).append(":");
			storedAddresses.stream().sorted().forEach(a -> errorMessageBuilder.append(" (ADDR: ")
					.append(a)
					.append(", L: ").append(currentPM.getLocation(a))
					.append(", V: ").append(currentPM.getVersion(a))
					.append(", A: ").append(currentPM.getAccess(a))
					.append(")"));
			errorMessageBuilder.append("\n");
		}

		errorMessageBuilder.append("Position map:\n").append(positionMap.toStringNonNull()).append("\n");

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

	protected void reset() {
		globalDelayRemoteInvocation = 0;
		ongoingAccessContext = null;
	}
}
