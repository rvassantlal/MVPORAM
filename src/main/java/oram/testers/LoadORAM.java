package oram.testers;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.structure.*;
import oram.messages.*;
import oram.security.EncryptionManager;
import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPathMap;
import oram.server.structure.EncryptedStash;
import oram.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.util.*;

public class LoadORAM {
	private final static Logger logger = LoggerFactory.getLogger("benchmarking");

	public static void main(String[] args) throws SecretSharingException {
		if (args.length != 3) {
			System.out.println("Usage: ... oram.testers.LoadORAM <treeHeight> <bucketSize> <blockSize>");
			System.exit(-1);
		}

		int oramId = 1;
		int initialClientId = 1000;

		int treeHeight = Integer.parseInt(args[0]);
		int bucketSize = Integer.parseInt(args[1]);
		int blockSize = Integer.parseInt(args[2]);

		LoadORAM oramLoader = new LoadORAM(initialClientId, oramId, treeHeight, bucketSize, blockSize);

		int treeSize = ORAMUtils.computeNumberOfNodes(treeHeight);

		long start = System.currentTimeMillis();
		oramLoader.load();
		long end = System.currentTimeMillis();

		oramLoader.close();

		long delay = end - start;
		long delayInSeconds = delay / 1000;
		long delayInMinutes = delayInSeconds / 60;
		logger.info("Took {} seconds ({} minutes) to write to {} addresses", delayInSeconds, delayInMinutes, treeSize);
		logger.info("LOAD ENDED");
	}

	private final ConfidentialServiceProxy serviceProxy;
	private final int oramId;
	private final EncryptionManager encryptionManager;
	private final ORAMContext oramContext;
	private int latestAccess;
	private final Set<Integer> missingTriples;
	private final PositionMap positionMap;
	private int nextBlockAddress;
	private final Logger loaderLogger = LoggerFactory.getLogger("benchmarking");

	private LoadORAM(int clientId, int oramId, int treeHeight, int bucketSize, int blockSize) throws SecretSharingException {
		this.serviceProxy = new ConfidentialServiceProxy(clientId);
		this.oramId = oramId;
		this.encryptionManager = new EncryptionManager();
		this.missingTriples = new HashSet<>();
		this.oramContext = createORAM(oramId, treeHeight, bucketSize, blockSize);
		this.positionMap = new PositionMap(oramContext.getTreeSize());
	}

	public void load() {
		int nPaths = 2 << (oramContext.getTreeHeight() - 1);
		int treeSize = ORAMUtils.computeNumberOfNodes(oramContext.getTreeHeight());

		int nBlocksToFillPerPath = (treeSize / (oramContext.getTreeHeight() + 1)) / nPaths;
		for (int pathId = 0; pathId < nPaths; pathId++) {
			PathMaps pathMapsHistory = getPathMaps();
			if (pathMapsHistory == null) {
				throw new IllegalStateException("Position map of oram " + oramId + " is null");
			}
			int opSequence = pathMapsHistory.getOperationSequence();

			consolidatePathMaps(pathMapsHistory);

			StashesAndPaths stashesAndPaths = getStashesAndPaths(pathId);
			if (stashesAndPaths == null) {
				throw new IllegalStateException("States and paths of oram " + oramId + " are null");
			}
			Stash mergedStash = mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths());

			int[] accessedPathLocations = ORAMUtils.computePathLocations(pathId, oramContext.getTreeHeight());
			boolean isEvicted = evict(accessedPathLocations, mergedStash, opSequence);
			if (!isEvicted) {
				throw new IllegalStateException("Failed to do eviction on oram " + oramId);
			}
			loaderLogger.info("Populated path {} out of {} ({} %) | Wrote addresses {} out of {} ({} %) | Stash: {}",
					(pathId + 1), nPaths, (int)(((pathId + 1) * 100.0) / nPaths),
					nextBlockAddress, treeSize, (int)((nextBlockAddress * 100.0) / treeSize),
					mergedStash.getBlocks().size());
		}
	}

	private PathMaps getPathMaps() {
		try {
			ORAMMessage request = new GetPathMaps(oramId, latestAccess, missingTriples);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_POSITION_MAP, request);

			Response response = serviceProxy.invokeOrderedHashed(serializedRequest);
			if (response == null || response.getPlainData() == null) {
				return null;
			}

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

	private StashesAndPaths getStashesAndPaths(int pathId) {
		try {
			ORAMMessage request = new StashPathORAMMessage(oramId, pathId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_STASH_AND_PATH, request);

			Response response = serviceProxy.invokeOrderedHashed(serializedRequest);
			if (response == null || response.getPlainData() == null) {
				return null;
			}

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
			for (Block block : bucket.getBlocks()) {
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

	public boolean evict(int[] accessedPathLocations, Stash stash, int opSequence) {
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		int pathCapacity = oramContext.getTreeLevels() * oramContext.getBucketSize();
		PathMap pathMap = new PathMap(pathCapacity);

		populatePath(accessedPathLocations, stash, path, pathMap, opSequence);

		for (Block block : stash.getBlocks().values()) {
			pathMap.setLocation(block.getAddress(), ORAMUtils.DUMMY_LOCATION, block.getVersion(),
					block.getAccess());
		}

		EncryptedStash encryptedStash = encryptionManager.encryptStash(stash);
		EncryptedPathMap encryptedPositionMap = encryptionManager.encryptPathMap(pathMap);

		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(oramContext, path);
		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath);
	}

	private void populatePath(int[] accessedPathLocations, Stash stash, Map<Integer, Bucket> pathToPopulate,
							  PathMap pathMap, int opSequence) {
		Set<Integer> bucketWithAvailableCapacity = new HashSet<>(accessedPathLocations.length);
		for (int pathLocation : accessedPathLocations) {
			bucketWithAvailableCapacity.add(pathLocation);
			Bucket bucket = new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize(), pathLocation);
			pathToPopulate.put(pathLocation, bucket);
		}

		Bucket bucket = pathToPopulate.get(accessedPathLocations[0]);
		for (int i = 0; i < oramContext.getBucketSize(); i++) {
			Block newBlock = new Block(oramContext.getBlockSize(), nextBlockAddress, opSequence,
					String.valueOf(nextBlockAddress).getBytes());

			bucket.putBlock(i, newBlock);
			pathMap.setLocation(nextBlockAddress, accessedPathLocations[0], opSequence, opSequence);
			nextBlockAddress++;
		}


		/*Set<Integer> blocksToEvict = new HashSet<>(stash.getBlocks().keySet());

		for (int address : blocksToEvict) {
			if (bucketWithAvailableCapacity.isEmpty()) {
				break;
			}
			Block block = stash.getAndRemoveBlock(address);
			int bucketId = positionMap.getLocation(address);

			//if it was not allocated to root bucket or the root bucket does not have space
			if (!bucketWithAvailableCapacity.contains(bucketId)) {
				throw new IllegalStateException("This should not happen");
			}

			Bucket bucket = pathToPopulate.get(bucketId);
			bucket.putBlock(block);
			if (bucket.isFull()) {
				bucketWithAvailableCapacity.remove(bucketId);
			}
			pathMap.setLocation(address, bucketId, block.getVersion(), block.getAccess());
		}

		for (int bucketId : bucketWithAvailableCapacity) {
			Bucket bucket = pathToPopulate.get(bucketId);
			while (!bucket.isFull()) {
				Block newBlock = new Block(oramContext.getBlockSize(), nextBlockAddress, opSequence,
						String.valueOf(nextBlockAddress).getBytes());
				bucket.putBlock(newBlock);
				pathMap.setLocation(nextBlockAddress, bucketId,opSequence, opSequence);
				nextBlockAddress++;
			}
		}*/
	}

	private boolean sendEvictionRequest(EncryptedStash encryptedStash, EncryptedPathMap encryptedPathMap,
										Map<Integer, EncryptedBucket> encryptedPath) {
		try {
			EvictionORAMMessage request = new EvictionORAMMessage(oramId, encryptedStash, encryptedPathMap, encryptedPath);
			byte[] serializedDataRequest = ORAMUtils.serializeRequest(ServerOperationType.EVICTION, request);

			boolean isSuccessful = sendRequestData(serializedDataRequest);
			if (!isSuccessful) {
				return false;
			}

			int hash = serviceProxy.getProcessId() + ORAMUtils.computeHashCode(serializedDataRequest) * 32;
			ORAMMessage dataHashRequest = new ORAMMessage(hash);//Sending request hash as oramId (not ideal implementation)
			byte[] serializedEvictionRequest = ORAMUtils.serializeRequest(ServerOperationType.EVICTION, dataHashRequest);

			Response response = serviceProxy.invokeOrdered(serializedEvictionRequest);

			if (response == null || response.getPlainData() == null) {
				return false;
			}
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

	private ORAMContext createORAM(int oramId, int treeHeight, int bucketSize, int blockSize) throws SecretSharingException {
		String password = encryptionManager.generatePassword();
		encryptionManager.createSecretKey(password);

		PositionMapType positionMapType = PositionMapType.TRIPLE_POSITION_MAP;

		EncryptedPathMap encryptedPathMap = initializeEmptyPathMap();
		EncryptedStash encryptedStash = initializeEmptyStash(blockSize);
		CreateORAMMessage request = new CreateORAMMessage(oramId, positionMapType,
				treeHeight, bucketSize, blockSize, encryptedPathMap, encryptedStash);
		byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.CREATE_ORAM, request);
		Response response = serviceProxy.invokeOrdered(serializedRequest, password.getBytes());
		if (response == null || response.getPlainData() == null) {
			throw new IllegalStateException("Failed to request creation of an ORAM");
		}

		Status status = Status.getStatus(response.getPlainData()[0]);
		if (status == Status.FAILED) {
			throw new IllegalStateException("Failed to create an ORAM");
		}

		return new ORAMContext(positionMapType, treeHeight, bucketSize, blockSize);
	}

	private EncryptedStash initializeEmptyStash(int blockSize) {
		Stash stash = new Stash(blockSize);
		return encryptionManager.encryptStash(stash);
	}

	private EncryptedPathMap initializeEmptyPathMap() {
		PathMap pathMap = new PathMap(1);
		return encryptionManager.encryptPathMap(pathMap);
	}

	public void close() {
		serviceProxy.close();
	}
}
