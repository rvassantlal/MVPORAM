package oram.benchmark.local;

import oram.client.EncryptionManager;
import oram.client.structure.*;
import oram.server.structure.*;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import oram.utils.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.SecureRandom;
import java.util.*;

public class LocalClient extends Thread {
	private final Logger logger = LoggerFactory.getLogger("benchmark.oram");
	private final int address;
	private final int clientId;
	private final LocalServers localServers;
	private final byte[] blockContent;
	private final ORAMContext oramContext;
	private final int nRequests;
	private final EncryptionManager encryptionManager;
	private final SecureRandom rndGenerator;
	private boolean isRealAccess;
	private byte[] oldContent;

	public LocalClient(int clientId, LocalServers localServers, ORAMContext oramContext, int nRequests) {
		super("Thread-Client-" + clientId);
		this.clientId = clientId;
		this.localServers = localServers;
		this.blockContent = new byte[oramContext.getBlockSize()];
		this.oramContext = oramContext;
		this.nRequests = nRequests;
		Arrays.fill(blockContent, (byte) 'a');
		int treeSize = ORAMUtils.computeTreeSize(oramContext.getTreeHeight(), oramContext.getBucketSize());
		this.address = clientId % treeSize;
		this.encryptionManager = new EncryptionManager();
		this.rndGenerator = new SecureRandom("oram".getBytes());
	}

	@Override
	public void run() {
		writeMemory(address, blockContent);
		byte[] oldContent;
		for (int i = 0; i < nRequests; i++) {
			if (clientId == 1) {
				//logger.info("[Client {}] Request {}", clientId, i);
			}
			oldContent = writeMemory(address, blockContent);
			if (!Arrays.equals(blockContent, oldContent)) {
				logger.error("[Client {}] Content at address {} is different ({})", clientId, address, Arrays.toString(oldContent));
				break;
			}
		}
	}

	private byte[] writeMemory(int address, byte[] blockContent) {
		return access(Operation.WRITE, address, blockContent);
	}

	private byte[] access(Operation op, int address, byte[] newContent) {
		isRealAccess = true;
		oldContent = null;
		PositionMaps oldPositionMaps = getPositionMaps();
		if (oldPositionMaps == null) {
			logger.error("[Client {}] Position map is null", clientId);
			return null;
		}
		PositionMap mergedPositionMap = mergePositionMaps(oldPositionMaps.getPositionMaps());
		int pathId = getPathId(mergedPositionMap, address);
		Stash mergedStash = getPS(pathId, op, address, newContent, oldPositionMaps, mergedPositionMap);
		boolean isEvicted = evict(mergedPositionMap, mergedStash, pathId, op, address,
				oldPositionMaps.getNewVersionId());

		if (!isEvicted) {
			logger.error("[Client {}] Failed to do eviction", clientId);
		}
		return oldContent;
	}

	private PositionMap mergePositionMaps(PositionMap[] positionMaps) {
		int treeSize = oramContext.getTreeSize();
		int[] pathIds = new int[treeSize];
		int[] versionIds = new int[treeSize];

		for (int address = 0; address < treeSize; address++) {
			int recentPathId = ORAMUtils.DUMMY_PATH;
			int recentVersionId = ORAMUtils.DUMMY_VERSION;
			for (PositionMap positionMap : positionMaps) {
				if (positionMap.getPathIds().length == 0)
					continue;
				int pathId = positionMap.getPathAt(address);
				int versionId = positionMap.getVersionIdAt(address);
				if (versionId > recentVersionId) {
					recentVersionId = versionId;
					recentPathId = pathId;
				}
			}
			pathIds[address] = recentPathId;
			versionIds[address] = recentVersionId;
		}
		return new PositionMap(versionIds, pathIds);
	}

	private int getPathId(PositionMap mergedPositionMap, int address) {
		int pathId = mergedPositionMap.getPathAt(address);
		if (pathId == ORAMUtils.DUMMY_PATH) {
			pathId = generateRandomPathId();
			this.isRealAccess = false;
		}
		return pathId;
	}

	private byte generateRandomPathId() {
		return (byte) rndGenerator.nextInt(1 << oramContext.getTreeHeight()); //2^height
	}

	private Stash getPS(int pathId, Operation op, int address, byte[] newContent,
						PositionMaps positionMaps, PositionMap mergedPositionMap) {
		StashesAndPaths stashesAndPaths = getStashesAndPaths(pathId);
		if (stashesAndPaths == null) {
			logger.error("[Client {}] States and paths are null", clientId);
			return null;
		}
		Stash mergedStash = mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths(),
				stashesAndPaths.getVersionPaths(), positionMaps, mergedPositionMap);

		Block block = mergedStash.getBlock(address);

		if (this.isRealAccess && op == Operation.READ) {
			if (block == null) {
				logger.error("[Client {}] Reading address {} from pathId {}", clientId, address, pathId);
				logger.error(positionMaps.toString());
				logger.error(stashesAndPaths.toString());
				logger.error(mergedPositionMap.toString());
				logger.error(mergedStash.toString());
			}else {
				oldContent = block.getContent();
			}
		} else if (op == Operation.WRITE) {
			if (block == null) {
				block = new Block(oramContext.getBlockSize(), address, newContent);
				mergedStash.putBlock(block);
			} else {
				oldContent = block.getContent();
				block.setContent(newContent);
			}
		}
		return mergedStash;
	}

	private Stash mergeStashesAndPaths(Map<Integer, Stash> stashes, Map<Integer, Bucket[]> paths,
									   Map<Integer, Set<Integer>> versionPaths, PositionMaps positionMaps,
									   PositionMap mergedPositionMap) {
		Map<Integer, Block> recentBlocks = new HashMap<>();
		Map<Integer, Integer> recentVersionIds = new HashMap<>();
		PositionMap[] positionMapsArray = positionMaps.getPositionMaps();
		int[] outstandingIds = positionMaps.getOutstandingVersionIds();
		Map<Integer, PositionMap> positionMapPerVersion = new HashMap<>(outstandingIds.length);
		for (int i = 0; i < outstandingIds.length; i++) {
			positionMapPerVersion.put(outstandingIds[i], positionMapsArray[i]);
		}

		mergeStashes(recentBlocks, recentVersionIds, stashes, versionPaths, positionMapPerVersion, mergedPositionMap);
		mergePaths(recentBlocks, recentVersionIds, paths, versionPaths, positionMapPerVersion, mergedPositionMap);

		Stash mergedStash = new Stash(oramContext.getBlockSize());
		for (Block recentBlock : recentBlocks.values()) {
			mergedStash.putBlock(recentBlock);
		}
		return mergedStash;
	}

	private void mergePaths(Map<Integer, Block> recentBlocks, Map<Integer, Integer> recentVersionIds,
							Map<Integer, Bucket[]> paths, Map<Integer, Set<Integer>> versionPaths,
							Map<Integer, PositionMap> positionMaps, PositionMap mergedPositionMap) {
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
		selectRecentBlocks(recentBlocks, recentVersionIds, versionPaths, positionMaps, mergedPositionMap, blocksToMerge);
	}

	private void mergeStashes(Map<Integer, Block> recentBlocks, Map<Integer, Integer> recentVersionIds,
							  Map<Integer, Stash> stashes, Map<Integer, Set<Integer>> versionPaths,
							  Map<Integer, PositionMap> positionMaps, PositionMap mergedPositionMap) {
		Map<Integer, List<Block>> blocksToMerge = new HashMap<>();
		for (Map.Entry<Integer, Stash> entry : stashes.entrySet()) {
			blocksToMerge.put(entry.getKey(), entry.getValue().getBlocks());
		}
		selectRecentBlocks(recentBlocks, recentVersionIds, versionPaths, positionMaps, mergedPositionMap, blocksToMerge);
	}

	private void selectRecentBlocks(Map<Integer, Block> recentBlocks, Map<Integer, Integer> recentVersionIds,
									Map<Integer, Set<Integer>> versionPaths, Map<Integer, PositionMap> positionMaps,
									PositionMap mergedPositionMap, Map<Integer, List<Block>> blocksToMerge) {
		for (Map.Entry<Integer, List<Block>> entry : blocksToMerge.entrySet()) {
			Set<Integer> outstandingTreeIds = versionPaths.get(entry.getKey());
			for (Block block : entry.getValue()) {
				for (int outstandingTreeId : outstandingTreeIds) {
					PositionMap outstandingPositionMap = positionMaps.get(outstandingTreeId);
					int blockAddress = block.getAddress();
					int blockVersionIdInOutstandingTree = outstandingPositionMap.getVersionIdAt(blockAddress);
					if (blockVersionIdInOutstandingTree == mergedPositionMap.getVersionIdAt(blockAddress)) {
						recentBlocks.put(blockAddress, block);
						recentVersionIds.put(blockAddress, blockVersionIdInOutstandingTree);
					}
				}
			}
		}
	}

	private boolean evict(PositionMap positionMap, Stash stash, int oldPathId,
						  Operation op, int changedAddress, int newVersionId) {
		byte newPathId = generateRandomPathId();
		if (op == Operation.WRITE) {
			positionMap.setVersionIdAt(changedAddress, newVersionId);
		}
		if (op == Operation.WRITE || (op == Operation.READ && oldPathId != ORAMUtils.DUMMY_PATH)) {
			positionMap.setPathAt(changedAddress, newPathId);
		}
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		Stash remainingBlocks = populatePath(positionMap, stash, oldPathId, path);

		EncryptedStash encryptedStash = encryptionManager.encryptStash(remainingBlocks);
		EncryptedPositionMap encryptedPositionMap = encryptionManager.encryptPositionMap(positionMap);
		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(oramContext, path);
		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath);
	}

	private Stash populatePath(PositionMap positionMap, Stash stash, int oldPathId,
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

	private PositionMaps getPositionMaps() {
		EncryptedPositionMaps[] encryptedPositionMaps = localServers.getPositionMaps(clientId);
		if (!areEqual(encryptedPositionMaps)) {
			logger.error("[Client {}] Position maps are different", clientId);
			return null;
		}
		return encryptionManager.decryptPositionMaps(encryptedPositionMaps[0]);
	}

	private StashesAndPaths getStashesAndPaths(int pathId) {
		EncryptedStashesAndPaths[] stashesAndPaths = localServers.getStashesAndPaths(clientId, pathId);
		if (!areEqual(stashesAndPaths)) {
			logger.error("[Client {}] Stashes and paths are different", clientId);
			return null;
		}
		return encryptionManager.decryptStashesAndPaths(oramContext, stashesAndPaths[0]);
	}

	private boolean sendEvictionRequest(EncryptedStash encryptedStash, EncryptedPositionMap encryptedPositionMap,
										Map<Integer, EncryptedBucket> encryptedPath) {

		return localServers.evict(clientId, encryptedStash, encryptedPositionMap, encryptedPath);
	}

	private <A extends Externalizable> boolean areEqual(A[] data) {
		byte[] firstElement = serialize(data[0]);
		for (A datum : data) {
			if (!Arrays.equals(firstElement, serialize(datum))) {
				return false;
			}
		}
		return true;
	}

	private <A extends Externalizable> byte[] serialize(A data) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			data.writeExternal(out);
			out.flush();
			bos.flush();
			return bos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
