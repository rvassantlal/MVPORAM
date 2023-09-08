package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.structure.*;
import oram.messages.EvictionORAMMessage;
import oram.messages.GetPositionMapMessage;
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
	private MergedPositionMap mergedPositionMap;


	public ORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
					  EncryptionManager encryptionManager) throws SecretSharingException {
		this.serviceProxy = serviceProxy;
		this.oramId = oramId;
		this.oramContext = oramContext;
		this.encryptionManager = encryptionManager;
		this.rndGenerator = new SecureRandom("oram".getBytes());
		this.mergedPositionMap = new MergedPositionMap(oramContext.getTreeSize());
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
		mergePositionMaps(oldPositionMaps.getPositionMaps(), oldPositionMaps.getNewVersionId()-1);
		int pathId = getPathId(mergedPositionMap, address);
		Stash mergedStash = getPS(pathId, op, address, newContent, oldPositionMaps);

		boolean isEvicted = evict(mergedStash, pathId, op, address,
				oldPositionMaps.getNewVersionId());

		if (!isEvicted) {
			logger.error("Failed to do eviction on oram {}", oramId);
		}
		return oldContent;
	}
//FOR DEBUG ONLY; NOT FOR PRODUCTION
	public void accessAllPaths() {
		int paths = 1 << oramContext.getTreeHeight();
		for (int i = 0; i < paths; i++) {
			this.isRealAccess = false;
			oldContent = null;
			PositionMaps oldPositionMaps = getPositionMaps(mergedPositionMap.getLatestSequenceNumber());
			mergePositionMaps(oldPositionMaps.getPositionMaps(), oldPositionMaps.getNewVersionId()-1);
			int pathId = i;
			logger.error("PATH:{}",i);
			Stash mergedStash = getPS(pathId, Operation.READ, -1, null, oldPositionMaps);
			logger.error(mergedPositionMap.toString());
			logger.error(mergedStash.toString());
			boolean isEvicted = dummyEvict(mergedStash, pathId);

			if (!isEvicted) {
				logger.error("Failed to do eviction on oram {}", oramId);
			}
		}

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
		Stash mergedStash = mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths(), pathId);

		Block block = mergedStash.getBlock(address);

		if(address == -1){
			logger.error(stashesAndPaths.toString());
		}

		if (this.isRealAccess && op == Operation.READ) {
			if (block == null) {
				logger.error("Reading address {} from pathId {}, with version {}", address, pathId,
						mergedPositionMap.getVersionIdAt(address));
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
		int newPathId = generateRandomPathIdByBucket(oldPathId);
		PositionMap positionMap = isRealAccess ? new PositionMap(mergedPositionMap.getVersionIdAt(changedAddress),
				oldPathId, changedAddress) : new PositionMap(mergedPositionMap.getVersionIdAt(changedAddress),
				ORAMUtils.DUMMY_PATH, changedAddress);
		if (op == Operation.WRITE) {
			positionMap.setVersionIdAt(changedAddress, newVersionId);
			mergedPositionMap.setVersionIdAt(changedAddress, newVersionId);
		}
		if (op == Operation.WRITE || (op == Operation.READ && isRealAccess)) {
			positionMap.setPathAt(changedAddress, newPathId);
			mergedPositionMap.setPathAt(changedAddress, newPathId);
		}
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		Stash remainingBlocks = populatePath(mergedPositionMap, stash, oldPathId, path);

		String password = ORAMUtils.generateRandomPassword(rndGenerator);
		SecretKey newEncryptionKey = encryptionManager.createSecretKey(password.toCharArray());

		EncryptedStash encryptedStash = encryptionManager.encryptStash(newEncryptionKey, remainingBlocks);
		EncryptedPositionMap encryptedPositionMap = encryptionManager.encryptPositionMap(newEncryptionKey, positionMap);
		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(newEncryptionKey, oramContext, path);
		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath, password);
	}
	public boolean dummyEvict(Stash stash, int oldPathId) {
		PositionMap positionMap = new PositionMap(ORAMUtils.DUMMY_VERSION,
				ORAMUtils.DUMMY_PATH, ORAMUtils.DUMMY_ADDRESS);
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		Stash remainingBlocks = populatePath(mergedPositionMap, stash, oldPathId, path);
		for (Map.Entry<Integer, Bucket> integerBucketEntry : path.entrySet()) {
			logger.error("BUCKET {}: {}",integerBucketEntry.getKey(),integerBucketEntry.getValue().toString());
		}
		String password = ORAMUtils.generateRandomPassword(rndGenerator);
		SecretKey newEncryptionKey = encryptionManager.createSecretKey(password.toCharArray());

		EncryptedStash encryptedStash = encryptionManager.encryptStash(newEncryptionKey, remainingBlocks);
		EncryptedPositionMap encryptedPositionMap = encryptionManager.encryptPositionMap(newEncryptionKey, positionMap);
		Map<Integer, EncryptedBucket> encryptedPath = encryptionManager.encryptPath(newEncryptionKey, oramContext, path);
		return sendEvictionRequest(encryptedStash, encryptedPositionMap, encryptedPath, password);
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

	private int generateRandomPathId() {
		return rndGenerator.nextInt(1 << oramContext.getTreeHeight()); //2^height
	}

	private int generateRandomPathIdByBucket(int oldPathId) {
		int bucketId = rndGenerator.nextInt(oramContext.getTreeLevels());
		List<Integer> myPath = ORAMUtils.computePathLocationsList(oldPathId,oramContext.getTreeHeight());
		int location = myPath.get(bucketId);
		ArrayList<Integer> possiblePaths = new ArrayList<>();
		for (int i = 0; i < (1 << oramContext.getTreeHeight()); i++) {
			List<Integer> toadd = ORAMUtils.computePathLocationsList(i, oramContext.getTreeHeight());
			if(toadd.contains(location)) {
				boolean possible = true;
				for (int j = bucketId-1; j >= 0; j--){
					if(myPath.contains(toadd.get(j))) {
						possible = false;
						break;
					}
				}
				if(possible)
					possiblePaths.add(i);
			}
		}
        return possiblePaths.get(rndGenerator.nextInt(possiblePaths.size()));
	}

	private Stash mergeStashesAndPaths(Map<Integer, Stash> stashes, Map<Integer, Bucket[]> paths, int pathId) {
		Map<Integer, Block> recentBlocks = new HashMap<>();

		mergeStashes(recentBlocks, stashes, pathId);
		mergePaths(recentBlocks, paths, pathId);

		Stash mergedStash = new Stash(oramContext.getBlockSize());
		for (Block recentBlock : recentBlocks.values()) {
			mergedStash.putBlock(recentBlock);
		}
		return mergedStash;
	}

	private void mergePaths(Map<Integer, Block> recentBlocks, Map<Integer, Bucket[]> paths, int pathId) {
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
		selectRecentBlocks(recentBlocks, blocksToMerge, pathId);
	}

	private void mergeStashes(Map<Integer, Block> recentBlocks, Map<Integer, Stash> stashes, int pathId) {
		Map<Integer, List<Block>> blocksToMerge = new HashMap<>();
		for (Map.Entry<Integer, Stash> entry : stashes.entrySet()) {
			if(entry.getValue() != null)
				blocksToMerge.put(entry.getKey(), entry.getValue().getBlocks());
		}
		selectRecentBlocks(recentBlocks, blocksToMerge, pathId);
	}

	private void selectRecentBlocks(Map<Integer, Block> recentBlocks, Map<Integer, List<Block>> blocksToMerge, int pathId) {
		for (Map.Entry<Integer, List<Block>> entry : blocksToMerge.entrySet()) {
			for (Block block : entry.getValue()) {
					int blockAddress = block.getAddress();
					int blockVersionId = block.getVersionId();
					if (blockVersionId == mergedPositionMap.getVersionIdAt(blockAddress)) {
						recentBlocks.put(blockAddress, block);
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

	private void mergePositionMaps(PositionMap[] positionMaps, int latestSequenceNumber) {
		int firstSequenceNumber = mergedPositionMap.getLatestSequenceNumber();
		int oldSequenceNumber = mergedPositionMap.getLatestSequenceNumber();
		for (int i = 0; i < positionMaps.length; i++) {
			if (positionMaps[i] == null && oldSequenceNumber == firstSequenceNumber){
				oldSequenceNumber += i;
				mergedPositionMap.setLatestSequenceNumber(oldSequenceNumber);
			} else if (positionMaps[i] != null) {
				PositionMap map = positionMaps[i];
				int address = map.getAddress();
				if(address != ORAMUtils.DUMMY_ADDRESS && map.getVersionId() >= mergedPositionMap.getVersionIdAt(address)) {
					mergedPositionMap.setPathAt(address, map.getPathId());
					mergedPositionMap.setVersionIdAt(address, map.getVersionId());
				}
			}
		}
		if(firstSequenceNumber == mergedPositionMap.getLatestSequenceNumber()){
			mergedPositionMap.setLatestSequenceNumber(latestSequenceNumber);
		}
	}

	private PositionMaps getPositionMaps(int latestSequenceNumber) {
		try {
			ORAMMessage request = new GetPositionMapMessage(oramId, latestSequenceNumber);
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
