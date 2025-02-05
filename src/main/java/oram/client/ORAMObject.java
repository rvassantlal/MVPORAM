package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.structure.*;
import oram.messages.*;
import oram.security.EncryptionManager;
import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPathMap;
import oram.server.structure.EncryptedStash;
import oram.utils.*;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.util.FastMath;
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
	private final UniformIntegerDistribution uniformDistribution;
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
		this.rndGenerator = new SecureRandom();
		this.positionMap = new PositionMap(oramContext.getTreeSize());
		this.latestAccess = 0; //server stores the initial position map and stash with version 1
		this.missingTriples = new HashSet<>();
		int pathCapacity = oramContext.getTreeLevels() * oramContext.getBucketSize();
		this.uniformDistribution = new UniformIntegerDistribution(0, pathCapacity - 1);// -1 because upper bound is inclusive
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
			logger.error("Path maps of oram {} is null", oramId);
			return null;
		}
		int opSequence = pathMapsHistory.getOperationSequence();
		logger.debug("Operation sequence: {}", opSequence);

		//ongoingAccessContext.setPathMapsHistory(pathMapsHistory);

		//Merging path maps to build tree map
		consolidatePathMaps(pathMapsHistory);


		end = System.nanoTime();
		delay = end - start;

		measurementLogger.info("M-receivedPM: {}", pathMapsHistory.getPathMaps().size());
		measurementLogger.info("M-map: {}", delay);

		//Translating address into bucket id
		int slot = positionMap.getLocation(address);
		int bucketId = (int) Math.floor((double) slot / oramContext.getBucketSize());

		//Extending bucket id to a path that include that bucket
		int pathId = getPathId(bucketId);

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

		//logger.info("[Client {}] {}", serviceProxy.getProcessId(), debugTracer);
		measurementLogger.info("M-eviction: {}", delay);
		measurementLogger.info("M-serviceCall: {}", globalDelayRemoteInvocation);
		if (!isEvicted) {
			logger.error("Failed to do eviction on oram {}", oramId);
		}

		return oldData;
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
		StringBuilder debugTracer = new StringBuilder();
		for (ArrayList<Bucket> buckets : snapshot.getTree()) {
			for (Bucket bucket : buckets) {
				debugTracer.append("Bucket ").append(bucket.getLocation()).append(": ").append(bucket).append("\n");
			}
		}
		logger.info("{}", debugTracer);
	}


	private PathMaps getPathMaps() {
		try {
			ORAMMessage request = new GetPathMaps(oramId, latestAccess, missingTriples);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_PM, request);

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

			return encryptionManager.decryptPathMaps(response.getPlainData());
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
		if (bucketId == ORAMUtils.BLOCK_IN_STASH) {
			return generateRandomPathId();
		}
		return randomWalkToLeafFrom(bucketId);
	}

	private int randomWalkToLeafFrom(int node) {
		int currentNode = node + 1;
		while (true) {
			int left = currentNode * 2;
			int right = left + 1;
			if (left >= oramContext.getTreeSize()) {
				return currentNode % (1 << oramContext.getTreeHeight());
			}
			boolean choice = rndGenerator.nextBoolean();
			if (choice) {
				currentNode = left;
			} else {
				currentNode = right;
			}
		}
	}

	private int generateRandomPathId() {
		return rndGenerator.nextInt(1 << oramContext.getTreeHeight()); //2^height
	}

	private StashesAndPaths getStashesAndPaths(int pathId) {
		try {
			ORAMMessage request = new StashPathORAMMessage(oramId, pathId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_PS, request);

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
				if (positionMapLocation == ORAMUtils.BLOCK_IN_STASH && positionMapVersion == version
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
			Block[] blocks = bucket.getBlocks();
			for (int index = 0; index < blocks.length; index++) {
				Block block = blocks[index];
				if (block == null)
					continue;
				int address = block.getAddress();
				int version = block.getVersion();
				int access = block.getAccess();
				int positionMapLocation = positionMap.getLocation(address);
				int bucketId = (int)Math.floor((double) positionMapLocation / oramContext.getBucketSize());
				int slotIndex = positionMapLocation % oramContext.getBucketSize();
				int positionMapVersion = positionMap.getVersion(address);
				int positionMapAccess = positionMap.getAccess(address);
				if (version > positionMapVersion
						|| (version == positionMapVersion && access > positionMapAccess)) {
					logger.warn("Block {} in path has a higher version than the position map (V: {}, A: {})",
							block, positionMapVersion, positionMapAccess);
					throw new IllegalStateException("Block's version in path is higher than in position map");
				}

				if (bucketId == bucket.getLocation() && slotIndex == index && positionMapVersion == version
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

		Stash newStash = populatePathAccessedBlockToStash(pathId, accessedAddress, stash, positionMap, path, pathMap);

		// Reordering of blocks in slots
		Stash workingSet = new Stash(oramContext.getBlockSize());
		for (Bucket bucket : path.values()) {
			Block[] blocks = bucket.getBlocks();
			for (Block block : blocks) {
				if (block != null) {
					workingSet.putBlock(block);
				}
			}
		}

		Iterator<Block> orderedBlocks = workingSet.getBlocks().values().stream().sorted(Comparator.comparingInt(Block::getAccess)).iterator();
		path.keySet().stream().sorted().forEach(bk -> {
			Bucket bucket = path.get(bk);
			Block[] blocks = bucket.getBlocks();
			for (int i = 0; i < blocks.length; i++) {
				if (blocks[i] != null) {
					Block orderedBlock = orderedBlocks.next();
					blocks[i] = orderedBlock;
					int location = bk * oramContext.getBucketSize() + i;
					pathMap.setLocation(orderedBlock.getAddress(), location, orderedBlock.getVersion(), orderedBlock.getAccess());
				}
			}
		});

		EncryptedStash encryptedStash = encryptionManager.encryptStash(newStash);
		EncryptedPathMap encryptedPositionMap = encryptionManager.encryptPathMap(pathMap);

		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(oramContext, path);
		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath);
	}

	private Stash populatePathAccessedBlockToStash(int pathId, int accessedAddress, Stash stash, PositionMap positionMap,
												   Map<Integer, Bucket> pathToPopulate, PathMap pathMap) {
		int[] accessedPathLocations = ORAMUtils.computePathLocations(pathId, oramContext.getTreeHeight());
		Arrays.sort(accessedPathLocations);

		for (int pathLocation : accessedPathLocations) {
			pathToPopulate.put(pathLocation,
					new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize(), pathLocation));
		}

		Set<Integer> blocksToEvict = new HashSet<>(stash.getBlocks().keySet());
		for (int address : blocksToEvict) {
			Block block = stash.getBlock(address);
			int location = positionMap.getLocation(address);
			if (location >= 0) {
				int bucketId = (int)Math.floor((double) location / oramContext.getBucketSize());
				int slotIndex = location % oramContext.getBucketSize();
				Bucket bucket = pathToPopulate.get(bucketId);
				Block blockInBucket = bucket.getBlock(slotIndex);
				if (blockInBucket == null || positionMap.getLocationUpdateAccess(block.getAddress()) >
						positionMap.getLocationUpdateAccess(blockInBucket.getAddress())) {
					bucket.putBlock(slotIndex, block);
					stash.getAndRemoveBlock(address);
					if (blockInBucket != null) {
						stash.putBlock(blockInBucket);
					}
				}
			}
		}

		//Sample K random slots from path to substitute
		int accessedBlockLocation = positionMap.getLocation(accessedAddress);
		Set<Integer> slots = new HashSet<>(oramContext.getBucketSize());
		if (accessedBlockLocation >= 0) {
			int bucketId = (int)Math.floor((double) accessedBlockLocation / oramContext.getBucketSize());
			int level = (int) FastMath.log(2, bucketId + 1);
			int slotIndex = accessedBlockLocation % oramContext.getBucketSize();
			int accessedBlockSlot = level * oramContext.getBucketSize() + slotIndex;
			slots.add(accessedBlockSlot);
		}

		int[] slotsToSubstitute = selectRandomSlots(slots, oramContext.getK());

		//Evict accessed block to new stash
		Stash newStash = new Stash(oramContext.getBlockSize());
		Block accessedBlock = stash.getAndRemoveBlock(accessedAddress);
		if (accessedBlock != null) {
			newStash.putBlock(accessedBlock);
			pathMap.setLocation(accessedBlock.getAddress(), ORAMUtils.BLOCK_IN_STASH, accessedBlock.getVersion(),
					accessedBlock.getAccess());
		}

		//Select min(K, |stash|) random blocks from stash to evict to path
		int nBlocks = Math.min(stash.size(), oramContext.getK());
		Block[] blocksToEvictToPath = selectRandomBlocks(stash, nBlocks);
		Iterator<Block> blocksToEvictToPathIterator = Arrays.stream(blocksToEvictToPath).iterator();

		//Substitute blocks in path
		for (int slot : slotsToSubstitute) {
			int level = slot / oramContext.getBucketSize();
			int index = slot % oramContext.getBucketSize();
			int bucketId = accessedPathLocations[level];
			int reverseSlot = bucketId * oramContext.getBucketSize() + index;

			Bucket bucket = pathToPopulate.get(bucketId);
			Block block = bucket.getBlock(index);
			if (block != null) {
				newStash.putBlock(block);
				pathMap.setLocation(block.getAddress(), ORAMUtils.BLOCK_IN_STASH, block.getVersion(), block.getAccess());
			}

			if (blocksToEvictToPathIterator.hasNext()) {
				Block blockToEvictToPath = blocksToEvictToPathIterator.next();
				bucket.putBlock(index, blockToEvictToPath);
				pathMap.setLocation(blockToEvictToPath.getAddress(), reverseSlot,
						blockToEvictToPath.getVersion(), blockToEvictToPath.getAccess());
			}
		}

		if (blocksToEvictToPathIterator.hasNext()) {
			throw new IllegalStateException("I should have evicted all K blocks from stash to path");
		}

		//Add remaining blocks in stash to new stash
		for (Block block : stash.getBlocks().values()) {
			newStash.putBlock(block);
			if (positionMap.getLocation(block.getAddress()) != ORAMUtils.BLOCK_IN_STASH) {
				pathMap.setLocation(block.getAddress(), ORAMUtils.BLOCK_IN_STASH, block.getVersion(), block.getAccess());
			}
		}

		return newStash;
	}

	private Block[] selectRandomBlocks(Stash stash, int nBlocks) {
		int[] blocksAddressesInStash = ORAMUtils.convertSetIntoOrderedArray(stash.getBlocks().keySet());
		Set<Integer> selectedBlocksIndexes = new HashSet<>();
		while (selectedBlocksIndexes.size() < nBlocks) {
			selectedBlocksIndexes.add(rndGenerator.nextInt(blocksAddressesInStash.length));
		}
		Block[] blocks = new Block[nBlocks];
		int index = 0;
		for (int selectedBlockIndex : selectedBlocksIndexes) {
			int selectedBlockAddress = blocksAddressesInStash[selectedBlockIndex];
			blocks[index] = stash.getAndRemoveBlock(selectedBlockAddress);
			index++;
		}
		return blocks;
	}

	private int[] selectRandomSlots(Set<Integer> slots, int nSlots) {
		while (slots.size() < nSlots) {
			slots.add(uniformDistribution.sample());
		}
		return ORAMUtils.convertSetIntoOrderedArray(slots);
	}

	private boolean sendEvictionRequest(EncryptedStash encryptedStash, EncryptedPathMap encryptedPathMap,
										Map<Integer, EncryptedBucket> encryptedPath) {
		try {
			EvictionORAMMessage request = new EvictionORAMMessage(oramId, encryptedStash, encryptedPathMap, encryptedPath);
			byte[] serializedDataRequest = ORAMUtils.serializeRequest(ServerOperationType.EVICT, request);

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
			byte[] serializedEvictionRequest = ORAMUtils.serializeRequest(ServerOperationType.EVICT, dataHashRequest);

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
		errorMessageBuilder.append("Path maps history:\n");
		/*for (Map.Entry<Integer, PathMap> entry : pathMapsHistory.getPathMaps().entrySet()) {
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
		}*/

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
