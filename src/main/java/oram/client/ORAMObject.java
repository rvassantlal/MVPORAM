package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.utils.ORAMUtils;
import oram.client.structure.*;
import oram.messages.EvictionORAMMessage;
import oram.messages.ORAMMessage;
import oram.messages.StashPathORAMMessage;
import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;
import oram.server.structure.ORAMContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oram.utils.Operation;
import oram.utils.ServerOperationType;
import oram.utils.Status;
import vss.facade.SecretSharingException;

import java.security.SecureRandom;
import java.util.*;

public class ORAMObject {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private final ConfidentialServiceProxy serviceProxy;
	private final int clientId;
	private final int oramId;
	private final ORAMContext oramContext;
	private final EncryptionManager encryptionManager;
	private final SecureRandom rndGenerator;

	public ORAMObject(ConfidentialServiceProxy serviceProxy, int clientId, int oramId, ORAMContext oramContext,
					  EncryptionManager encryptionManager) throws SecretSharingException {
		this.serviceProxy = serviceProxy;
		this.clientId = clientId;
		this.oramId = oramId;
		this.oramContext = oramContext;
		this.encryptionManager = encryptionManager;
		this.rndGenerator = new SecureRandom("oram".getBytes());
	}

	/**
	 * Read the memory address.
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
		boolean isRealAccess = true;
		PositionMap[] positionMaps = getPositionMaps();
		if (positionMaps == null) {
			logger.error("Position map of oram {} is null", oramId);
			return null;
		}

		PositionMap mergedPositionMap = mergePositionMaps(positionMaps);

		byte pathId = mergedPositionMap.getPathAt(address);
		logger.debug("Real path id: {}", pathId);
		byte newPathId = generateRandomPathId();
		logger.debug("New path id: {}", newPathId);
		if (op == Operation.WRITE || (op == Operation.READ && pathId != ORAMUtils.DUMMY_PATH)) {
			mergedPositionMap.setPathAt(address, newPathId);
			mergedPositionMap.setVersionIdAt(address, generateVersion(mergedPositionMap));
		}

		if (pathId == ORAMUtils.DUMMY_PATH) {
			pathId = generateRandomPathId();
			logger.debug("Dummy path id: {}", pathId);
			isRealAccess = false;
		}

		StashesAndPaths stashesAndPaths = getStashesAndPaths(pathId);
		if (stashesAndPaths == null) {
			logger.error("States and paths of oram {} are null", oramId);
			return null;
		}

		Stash mergedStash = mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths());

		Block block = mergedStash.getBlock(address);
		byte[] oldContent = null;
		if (isRealAccess && op == Operation.READ) {
			oldContent = block.getContent();
		} else if (op == Operation.WRITE){
			if (block == null) {
				block = new Block(oramContext.getBlockSize(), address, newContent);
				mergedStash.putBlock(block);
			} else {
				block.setContent(newContent);
			}
		}

		boolean isEvicted = evict(mergedPositionMap, mergedStash, pathId);
		if (!isEvicted) {
			logger.error("Failed to do eviction on oram {}", oramId);
		}
		return oldContent;
	}

	private boolean evict(PositionMap positionMap, Stash stash, byte oldPathId) {
		int[] oldPathLocations = ORAMUtils.computePathLocations(oldPathId, oramContext.getTreeHeight());
		Map<Byte, List<Integer>> commonPaths = new HashMap<>();
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		for (int pathLocation : oldPathLocations) {
			path.put(pathLocation, new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize()));
		}
		Stash remainingBlocks = new Stash(oramContext.getBlockSize());
		for (Block block : stash.getBlocks()) {
			int address = block.getAddress();
			byte pathId = positionMap.getPathAt(address);
			List<Integer> commonPath = commonPaths.get(pathId);
			if (commonPath == null) {
				int[] pathLocations = ORAMUtils.computePathLocations(pathId, oramContext.getTreeHeight());
				commonPath = ORAMUtils.computePathIntersection(oramContext.getTreeLevels(), oldPathLocations, pathLocations);
				commonPaths.put(pathId, commonPath);
			}
			boolean isPathEmpty = false;
			for (int pathLocation : commonPath) {
				Bucket bucket = path.get(pathLocation);
				if (bucket.putBlock(block)) {
					isPathEmpty = true;
					break;
				}
			}
			if (!isPathEmpty) {
				remainingBlocks.putBlock(block);
			}
		}
		EncryptedStash encryptedStash = encryptionManager.encryptStash(remainingBlocks);
		EncryptedPositionMap encryptedPositionMap = encryptionManager.encryptPositionMap(positionMap);
		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(oramContext, path);
		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath);
	}

	private double generateVersion(PositionMap positionMap) {
		double maxVersion = 0;
		for (double versionId : positionMap.getVersionIds()) {
			maxVersion = Math.max(maxVersion, versionId);
		}
		int versionLevel = (int)maxVersion;
		return Double.parseDouble(versionLevel + "." + clientId);
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

	private Stash mergeStashesAndPaths(Map<Double, Stash> stashes, Map<Double, Bucket[]> paths) {
		Map<Integer, Block> recentBlocks = new HashMap<>();
		Map<Integer, Double> recentVersionIds = new HashMap<>();

		mergeStashes(recentBlocks, recentVersionIds, stashes);
		mergePaths(recentBlocks, recentVersionIds, paths);

		Stash mergedStash = new Stash(oramContext.getBlockSize());
		for (Block recentBlock : recentBlocks.values()) {
			mergedStash.putBlock(recentBlock);
		}
		return mergedStash;
	}

	private void mergePaths(Map<Integer, Block> recentBlocks, Map<Integer, Double> recentVersionIds,
							Map<Double, Bucket[]> paths) {
		Map<Double, List<Block>> blocksToMerge = new HashMap<>();
		for (Map.Entry<Double, Bucket[]> entry : paths.entrySet()) {
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

	private void mergeStashes(Map<Integer, Block> recentBlocks, Map<Integer, Double> recentVersionIds,
							  Map<Double, Stash> stashes) {
		Map<Double, List<Block>> blocksToMerge = new HashMap<>();
		for (Map.Entry<Double, Stash> entry : stashes.entrySet()) {
			blocksToMerge.put(entry.getKey(), entry.getValue().getBlocks());
		}
		selectRecentBlocks(recentBlocks, recentVersionIds, blocksToMerge);
	}

	private void selectRecentBlocks(Map<Integer, Block> recentBlocks, Map<Integer, Double> recentVersionIds,
									Map<Double, List<Block>> blocks) {
		for (Map.Entry<Double, List<Block>> entry : blocks.entrySet()) {
			for (Block block : entry.getValue()) {
				if (recentVersionIds.containsKey(block.getAddress())) {
					double versionId = recentVersionIds.get(block.getAddress());
					if (entry.getKey() > versionId) {
						recentBlocks.put(block.getAddress(), block);
						recentVersionIds.put(block.getAddress(), versionId);
					}
				} else {
					recentVersionIds.put(block.getAddress(), entry.getKey());
					recentBlocks.put(block.getAddress(), block);
				}
			}
		}
	}

	private StashesAndPaths getStashesAndPaths(byte pathId) {
		try {
			ORAMMessage request = new StashPathORAMMessage(oramId, pathId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_STASH_AND_PATH, request);
			if (serializedRequest == null) {
				return null;
			}
			Response response = serviceProxy.invokeUnordered(serializedRequest);
			if (response == null || response.getPainData() == null)
				return null;

			return encryptionManager.decryptStashesAndPaths(oramContext, response.getPainData());
		} catch (SecretSharingException e) {
			return null;
		}
	}

	private PositionMap mergePositionMaps(PositionMap[] positionMaps) {
		int treeSize = oramContext.getTreeSize();
		byte[] pathIds = new byte[treeSize];
		double[] versionIds = new double[treeSize];

		for (int address = 0; address < treeSize; address++) {
			byte recentPathId = ORAMUtils.DUMMY_PATH;
			double recentVersionId = ORAMUtils.DUMMY_VERSION;
			for (PositionMap positionMap : positionMaps) {
				if (positionMap.getPathIds().length == 0)
					continue;
				byte pathId = positionMap.getPathAt(address);
				double versionId = positionMap.getVersionIdAt(address);
				if (versionId != ORAMUtils.DUMMY_VERSION && versionId > recentVersionId) {
					recentVersionId = versionId;
					recentPathId = pathId;
				}
			}
			pathIds[address] = recentPathId;
			versionIds[address] = recentVersionId;
		}
		return new PositionMap(versionIds, pathIds);
	}

	private PositionMap[] getPositionMaps() {
		try {
			ORAMMessage request = new ORAMMessage(oramId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_POSITION_MAP, request);
			if (serializedRequest == null) {
				return null;
			}
			Response response = serviceProxy.invokeOrdered(serializedRequest);
			if (response == null || response.getPainData() == null)
				return null;
			return encryptionManager.decryptPositionMaps(response.getPainData());
		} catch (SecretSharingException e) {
			return null;
		}
	}
}
