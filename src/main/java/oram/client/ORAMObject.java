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
import vss.facade.Mode;
import vss.facade.SecretSharingException;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
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

	public ORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
					  EncryptionManager encryptionManager) throws SecretSharingException {
		this.serviceProxy = serviceProxy;
		this.oramId = oramId;
		this.oramContext = oramContext;
		this.encryptionManager = encryptionManager;
		this.rndGenerator = new SecureRandom("oram".getBytes());
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
		PositionMaps oldPositionMaps = getPositionMaps();
		if (oldPositionMaps == null) {
			logger.error("Position map of oram {} is null", oramId);
			return null;
		}

		PositionMap mergedPositionMap = mergePositionMaps(oldPositionMaps.getPositionMaps());
		int pathId = getPathId(mergedPositionMap, address);
		Stash mergedStash = getPS(pathId, op, address, newContent, oldPositionMaps, mergedPositionMap);
		boolean isEvicted = evict(mergedPositionMap, mergedStash, pathId, op, address,
				oldPositionMaps.getNewVersionId());

		if (!isEvicted) {
			logger.error("Failed to do eviction on oram {}", oramId);
		}
		return oldContent;
	}

	public int getPathId(PositionMap mergedPositionMap, int address) {
		int pathId = mergedPositionMap.getPathAt(address);
		if (pathId == ORAMUtils.DUMMY_PATH) {
			pathId = generateRandomPathId();
			this.isRealAccess = false;
		}
		return pathId;
	}

	public Stash getPS(int pathId, Operation op, int address, byte[] newContent,
					   PositionMaps positionMaps, PositionMap mergedPositionMap) {
		StashesAndPaths stashesAndPaths = getStashesAndPaths(pathId);
		if (stashesAndPaths == null) {
			logger.error("States and paths of oram {} are null", oramId);
			return null;
		}
		Stash mergedStash = mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths(),
				stashesAndPaths.getVersionPaths(), positionMaps, mergedPositionMap);

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
				block = new Block(oramContext.getBlockSize(), address, newContent);
				mergedStash.putBlock(block);
			} else {
				oldContent = block.getContent();
				block.setContent(newContent);
			}
		}
		return mergedStash;
	}

	public boolean evict(PositionMap positionMap, Stash stash, int oldPathId,
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

		String password = ORAMUtils.generateRandomPassword(rndGenerator);
		SecretKey newEncryptionKey = encryptionManager.createSecretKey(password.toCharArray());

		EncryptedStash encryptedStash = encryptionManager.encryptStash(newEncryptionKey, remainingBlocks);
		EncryptedPositionMap encryptedPositionMap = encryptionManager.encryptPositionMap(newEncryptionKey, positionMap);
		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(newEncryptionKey, oramContext, path);
		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath, password);
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

	private boolean sendEvictionRequest(EncryptedStash encryptedStash, EncryptedPositionMap encryptedPositionMap,
										Map<Integer, EncryptedBucket> encryptedPath, String password) {
		try {
			ORAMMessage request = new EvictionORAMMessage(oramId, encryptedStash, encryptedPositionMap, encryptedPath);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.EVICTION, request);
			if (serializedRequest == null) {
				return false;
			}

			Response response = serviceProxy.invokeOrdered(serializedRequest, Mode.SMALL_SECRET, password.getBytes());
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
	private StashesAndPaths getStashesAndPaths(int pathId) {
		try {
			ORAMMessage request = new StashPathORAMMessage(oramId, pathId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_STASH_AND_PATH, request);
			if (serializedRequest == null) {
				return null;
			}
			Response response = serviceProxy.invokeOrdered(serializedRequest);
			if (response == null)
				return null;
			byte[][] confidentialData = response.getConfidentialData();
			if (response.getPainData() == null || confidentialData == null || confidentialData.length == 0)
				return null;

			try (ByteArrayInputStream bis = new ByteArrayInputStream(response.getPainData());
				 ObjectInputStream in = new ObjectInputStream(bis)) {
				byte[] serializedEncryptedStashesAndPaths = new byte[in.readInt()];
				in.readFully(serializedEncryptedStashesAndPaths);
				int nVersions = in.readInt();
				Map<Integer, SecretKey> decryptionKeys = new HashMap<>(nVersions);
				for (int i = 0; i < nVersions; i++) {
					int version = in.readInt();
					String password = new String(confidentialData[i]);
					SecretKey secretKey = encryptionManager.createSecretKey(password.toCharArray());
					decryptionKeys.put(version, secretKey);
				}

				return encryptionManager.decryptStashesAndPaths(decryptionKeys, oramContext,
						serializedEncryptedStashesAndPaths);
			}
		} catch (SecretSharingException | IOException e) {
			return null;
		}
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

	private PositionMaps getPositionMaps() {
		try {
			ORAMMessage request = new ORAMMessage(oramId);
			byte[] serializedRequest = ORAMUtils.serializeRequest(ServerOperationType.GET_POSITION_MAP, request);
			if (serializedRequest == null) {
				return null;
			}
			Response response = serviceProxy.invokeOrdered(serializedRequest);
			if (response == null)
				return null;
			byte[][] confidentialData = response.getConfidentialData();
			if (response.getPainData() == null || confidentialData == null || confidentialData.length == 0)
				return null;
			SecretKey[] decryptionKeys = new SecretKey[confidentialData.length];
			for (int i = 0; i < confidentialData.length; i++) {
				String password = new String(confidentialData[i]);
				decryptionKeys[i] = encryptionManager.createSecretKey(password.toCharArray());
			}
			return encryptionManager.decryptPositionMaps(decryptionKeys, response.getPainData());
		} catch (SecretSharingException e) {
			return null;
		}
	}
}
