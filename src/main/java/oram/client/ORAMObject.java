package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.structure.*;
import oram.messages.EvictionORAMMessage;
import oram.messages.GetPositionMapMessage;
import oram.messages.ORAMMessage;
import oram.messages.StashPathORAMMessage;
import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;
import oram.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import java.security.SecureRandom;
import java.util.*;

public class ORAMObject {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private final ConfidentialServiceProxy serviceProxy;
	private final int oramId;
	private final ORAMContext oramContext;
	private final EncryptionManager encryptionManager;
	private final SecureRandom rndGenerator;
	byte[] oldContent = null;
	private boolean isRealAccess;

	MergedPositionMap mergedPositionMap;

	public ORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
					  EncryptionManager encryptionManager) throws SecretSharingException {
		this.serviceProxy = serviceProxy;
		this.oramId = oramId;
		this.oramContext = oramContext;
		this.encryptionManager = encryptionManager;
		this.rndGenerator = new SecureRandom("oram".getBytes());
		this.mergedPositionMap = new MergedPositionMap(new int[oramContext.getTreeSize()],
														new int[oramContext.getTreeSize()]);
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
		byte[] result = access(Operation.READ, address, null);
		System.gc();
		return result;
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
		byte[] result = access(Operation.WRITE, address, content);
		System.gc();
		return result;
	}

	private byte[] access(Operation op, int address, byte[] newContent) {
		this.isRealAccess = true;
		oldContent = null;
		PositionMaps oldPositionMaps = getPositionMaps(mergedPositionMap.getLatestSequenceNumber());
		if (oldPositionMaps == null) {
			logger.error("Position map of oram {} is null", oramId);
			return null;
		}
		mergedPositionMap.setLatestSequenceNumber(oldPositionMaps.getNewVersionId());
		mergePositionMaps(oldPositionMaps.getPositionMaps());
		int pathId = getPathId(mergedPositionMap, address);
		Stash mergedStash = getPS(pathId, op, address, newContent, oldPositionMaps);

		boolean isEvicted = evict(mergedStash, pathId, op, address,
				oldPositionMaps.getNewVersionId());

		if (!isEvicted) {
			logger.error("Failed to do eviction on oram {}", oramId);
		}
		return oldContent;
	}

	public int getPathId(MergedPositionMap mergedPositionMap, int address) {
		int pathId = mergedPositionMap.getPathAt(address);
		if (pathId == ORAMUtils.DUMMY_PATH) {
			pathId = generateRandomPathId();
			this.isRealAccess = false;
		}
		return pathId;
	}

	public Stash getPS(int pathId, Operation op, int address, byte[] newContent,
					   PositionMaps positionMaps) {
		StashesAndPaths stashesAndPaths = getStashesAndPaths(pathId);
		if (stashesAndPaths == null) {
			logger.error("States and paths of oram {} are null", oramId);
			return null;
		}
		Stash mergedStash = mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths());

		Block block = mergedStash.getBlock(address);

		if (this.isRealAccess && op == Operation.READ) {
			if (block == null) {
				logger.error("Reading address {} from pathId {}", address, pathId);
				logger.error(positionMaps.toString());
				logger.error(stashesAndPaths.toString());
				logger.error(mergedPositionMap.toString());
				logger.error(mergedStash.toString());
			}else {
				oldContent = block.getContent();
			}
		} else if (op == Operation.WRITE) {
			if (block == null) {
				block = new Block(oramContext.getBlockSize(), address, positionMaps.getNewVersionId(), newContent);
				mergedStash.putBlock(block);
			} else {
				oldContent = block.getContent();
				block.setContent(newContent);
				block.setVersionId(positionMaps.getNewVersionId());
			}
		}
		return mergedStash;
	}

	public boolean evict(Stash stash, int oldPathId,
						 Operation op, int changedAddress, int newVersionId) {
		byte newPathId = generateRandomPathId();
		PositionMap positionMap = new PositionMap(mergedPositionMap.getVersionIdAt(changedAddress), oldPathId, changedAddress);
		if (op == Operation.WRITE) {
			positionMap.setVersionIdAt(changedAddress, newVersionId);
			mergedPositionMap.setVersionIdAt(changedAddress, newVersionId);
		}
		if (op == Operation.WRITE || (op == Operation.READ && oldPathId != ORAMUtils.DUMMY_PATH)) {
			positionMap.setPathAt(changedAddress, newPathId);
			mergedPositionMap.setPathAt(changedAddress, newPathId);
		}
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		Stash remainingBlocks = populatePath(mergedPositionMap, stash, oldPathId, path);
		EncryptedStash encryptedStash = encryptionManager.encryptStash(remainingBlocks);
		EncryptedPositionMap encryptedPositionMap = encryptionManager.encryptPositionMap(positionMap);
		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(oramContext, path);
		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath);
	}

	private Stash populatePath(MergedPositionMap positionMap, Stash stash, int oldPathId,
							   Map<Integer, Bucket> pathToPopulate) {
		int[] oldPathLocations = ORAMUtils.computePathLocations(oldPathId, oramContext.getTreeHeight());
		Map<Integer, List<Integer>> commonPaths = new HashMap<>();
		for (int pathLocation : oldPathLocations) {
			pathToPopulate.put(pathLocation, new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize()));
		}
		Stash remainingBlocks = new Stash(oramContext.getBlockSize());
		for (Block block : stash.getBlocks()) {
			int address = block.getAddress();
			int pathId = positionMap.getPathAt(address);
			List<Integer> commonPath = commonPaths.get(pathId);
			if (commonPath == null) {
				int[] pathLocations = ORAMUtils.computePathLocations(pathId, oramContext.getTreeHeight());
				commonPath = ORAMUtils.computePathIntersection(oramContext.getTreeLevels(), oldPathLocations,
						pathLocations);
				commonPaths.put(pathId, commonPath);
			}
			boolean isPathEmpty = false;
			for (int pathLocation : commonPath) {
				Bucket bucket = pathToPopulate.get(pathLocation);
				if (bucket.putBlock(block)) {
					isPathEmpty = true;
					break;
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
			ORAMMessage request = new EvictionORAMMessage(oramId, encryptedStash, encryptedPositionMap, encryptedPath);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.EVICTION, request);
			if (serializedRequest == null) {
				return false;
			}

			Response response = serviceProxy.invokeOrdered(serializedRequest);
			if (response == null || response.getPainData() == null) {
				return false;
			}
			Status status = Status.getStatus(response.getPainData()[0]);
			return status != Status.FAILED;
		} catch (SecretSharingException e) {
			return false;
		}
	}

	private byte generateRandomPathId() {
		return (byte) rndGenerator.nextInt(1 << oramContext.getTreeHeight()); //2^height
	}

	private Stash mergeStashesAndPaths(Map<Integer, Stash> stashes, Map<Integer, Bucket[]> paths) {
		Map<Integer, Block> recentBlocks = new HashMap<>();
		Map<Integer, Integer> recentVersionIds = new HashMap<>();

		mergeStashes(recentBlocks, recentVersionIds, stashes);
		mergePaths(recentBlocks, recentVersionIds, paths);

		Stash mergedStash = new Stash(oramContext.getBlockSize());
		for (Block recentBlock : recentBlocks.values()) {
			mergedStash.putBlock(recentBlock);
		}
		return mergedStash;
	}

	private void mergePaths(Map<Integer, Block> recentBlocks, Map<Integer, Integer> recentVersionIds,
							Map<Integer, Bucket[]> paths) {
		Map<Integer, List<Block>> blocksToMerge = new HashMap<>();
		for (Map.Entry<Integer, Bucket[]> entry : paths.entrySet()) {
			List<Block> blocks = new LinkedList<>();
			for (Bucket bucket : entry.getValue()) {
				for (Block block : bucket.readBucket()) {
					if (block != null) {
						blocks.add(block);
					}
				}
			}
			blocksToMerge.put(entry.getKey(), blocks);
		}
		selectRecentBlocks(recentBlocks, recentVersionIds, blocksToMerge);
	}

	private void mergeStashes(Map<Integer, Block> recentBlocks, Map<Integer, Integer> recentVersionIds,
							  Map<Integer, Stash> stashes) {
		Map<Integer, List<Block>> blocksToMerge = new HashMap<>();
		for (Map.Entry<Integer, Stash> entry : stashes.entrySet()) {
			blocksToMerge.put(entry.getKey(), entry.getValue().getBlocks());
		}
		selectRecentBlocks(recentBlocks, recentVersionIds, blocksToMerge);
	}

	private void selectRecentBlocks(Map<Integer, Block> recentBlocks, Map<Integer, Integer> recentVersionIds,
									 Map<Integer, List<Block>> blocksToMerge) {
		for (Map.Entry<Integer, List<Block>> entry : blocksToMerge.entrySet()) {
			for (Block block : entry.getValue()) {
					int blockAddress = block.getAddress();
					int blockVersionId = block.getVersionId();
					if (blockVersionId == mergedPositionMap.getVersionIdAt(blockAddress)) {
						recentBlocks.put(blockAddress, block);
						recentVersionIds.put(blockAddress, blockVersionId);
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
			Response response = serviceProxy.invokeOrderedHashed(serializedRequest);
			if (response == null || response.getPainData() == null)
				return null;

			return encryptionManager.decryptStashesAndPaths(oramContext, response.getPainData());
		} catch (SecretSharingException e) {
			return null;
		}
	}

	private void mergePositionMaps(PositionMap[] positionMaps) {
		for (PositionMap map:positionMaps) {
			int address = map.getAddress();
			if(map.getVersionId() > mergedPositionMap.getVersionIdAt(address)) {
				mergedPositionMap.setPathAt(address, map.getPathId());
				mergedPositionMap.setVersionIdAt(address, map.getVersionId());
			}
		}
	}

	private PositionMaps getPositionMaps(int latestSequenceNumber) {
		try {
			ORAMMessage request = new GetPositionMapMessage(oramId, latestSequenceNumber);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_POSITION_MAP, request);
			if (serializedRequest == null) {
				return null;
			}
			Response response = serviceProxy.invokeOrderedHashed(serializedRequest);
			if (response == null || response.getPainData() == null)
				return null;
			return encryptionManager.decryptPositionMaps(response.getPainData());
		} catch (SecretSharingException e) {
			return null;
		}
	}
}
