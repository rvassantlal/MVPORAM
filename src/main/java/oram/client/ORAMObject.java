package oram.client;

import confidential.client.ConfidentialServiceProxy;
import confidential.client.Response;
import oram.client.structure.*;
import oram.messages.EvictionORAMMessage;
import oram.messages.ORAMMessage;
import oram.messages.StashPathORAMMessage;
import oram.security.AbstractEncryptionManager;
import oram.server.structure.EncryptedBucket;
import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;
import oram.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vss.facade.SecretSharingException;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ORAMObject {
	protected final Logger logger = LoggerFactory.getLogger("oram");
	protected final ConfidentialServiceProxy serviceProxy;
	protected final int oramId;
	protected final ORAMContext oramContext;
	protected final AbstractEncryptionManager encryptionManager;
	private final SecureRandom rndGenerator;
	byte[] oldContent = null;
	private boolean isRealAccess;
	private boolean isMeasure;

	public ORAMObject(ConfidentialServiceProxy serviceProxy, int oramId, ORAMContext oramContext,
					  AbstractEncryptionManager encryptionManager) {
		this.serviceProxy = serviceProxy;
		this.oramId = oramId;
		this.oramContext = oramContext;
		this.encryptionManager = encryptionManager;
		this.rndGenerator = new SecureRandom("oram".getBytes());
	}

	public void measure() {
		isMeasure = true;
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

		long start, end, delay;

		start = System.nanoTime();
		PositionMaps oldPositionMaps = getPositionMaps();
		if (oldPositionMaps == null) {
			logger.error("Position map of oram {} is null", oramId);
			return null;
		}
		end = System.nanoTime();
		delay = end - start;
		if (isMeasure) {
			logger.info("MGetPMOP: {}", delay);
		}

		PositionMap mergedPositionMap = mergePositionMaps(oldPositionMaps);
		if (mergedPositionMap == null) {
			logger.error("Failed to merge position maps of oram {}", oramId);
			return null;
		}

		int pathId = getPathId(mergedPositionMap, address);

		start = System.nanoTime();
		Stash mergedStash = getPS(pathId, op, address, newContent, oldPositionMaps.getNewVersionId(), mergedPositionMap);
		end = System.nanoTime();
		delay = end - start;
		if (isMeasure) {
			logger.info("MGetPSOP: {}", delay);
		}

		start = System.nanoTime();
		boolean isEvicted = evict(mergedPositionMap, mergedStash, pathId, op, address, oldPositionMaps.getNewVersionId());
		end = System.nanoTime();
		delay = end - start;
		if (isMeasure) {
			logger.info("MEvictionOP: {}", delay);
		}

		if (!isEvicted) {
			logger.error("Failed to do eviction on oram {}", oramId);
		}
		return oldContent;
	}

	protected void reset() {
		oldContent = null;
		isRealAccess = true;
	}

	public int getPathId(PositionMap mergedPositionMap, int address) {
		int pathId = mergedPositionMap.getPathAt(address);
		logger.debug("Getting pathId {} for address {} and version {}", pathId, address,
				mergedPositionMap.getVersionIdAt(address));
		if (pathId == ORAMUtils.DUMMY_PATH) {
			pathId = generateRandomPathId();
			this.isRealAccess = false;
		}
		return pathId;
	}

	public Stash getPS(int pathId, Operation op, int address, byte[] newContent,
					   int newVersionId, PositionMap mergedPositionMap) {
		StashesAndPaths stashesAndPaths = getStashesAndPaths(pathId);
		if (stashesAndPaths == null) {
			logger.error("States and paths of oram {} are null", oramId);
			return null;
		}
		Stash mergedStash = mergeStashesAndPaths(stashesAndPaths.getStashes(), stashesAndPaths.getPaths(),
				mergedPositionMap);

		Block block = mergedStash.getBlock(address);

		if (this.isRealAccess && op == Operation.READ) {
			if (block == null) {
				logger.error("[client {}] Reading address {} from pathId {}", serviceProxy.getProcessId(), address, pathId);
				logger.error("[client {}] {}", serviceProxy.getProcessId(), stashesAndPaths);
				logger.error("[client {}] {}", serviceProxy.getProcessId(), mergedPositionMap.toString());
				logger.error("[client {}] {}", serviceProxy.getProcessId(), mergedStash);
				throw new IllegalStateException("Block is null");
				//System.exit(-1);
			} else {
				oldContent = block.getContent();
			}
		} else if (op == Operation.WRITE) {
			if (block == null) {
				block = new Block(oramContext.getBlockSize(), address, newVersionId, newContent);
				mergedStash.putBlock(block);
			} else {
				oldContent = block.getContent();
				block.setContent(newContent);
				block.setVersionId(newVersionId);
			}
		}
		return mergedStash;
	}

	public boolean evict(PositionMap mergedPositionMap, Stash stash, int oldPathId,
						 Operation op, int changedAddress, int newVersionId) {
		int newPathId = generateRandomPathId();
		PositionMap updatedPositionMap = updatePositionMap(op, mergedPositionMap, isRealAccess, changedAddress,
				newPathId, newVersionId);
		Map<Integer, Bucket> path = new HashMap<>(oramContext.getTreeLevels());
		Stash remainingBlocks = populatePath(mergedPositionMap, stash, oldPathId, path);

		String password = encryptionManager.generatePassword();
		SecretKey newEncryptionKey = encryptionManager.createSecretKey(password);

		EncryptedStash encryptedStash = encryptionManager.encryptStash(newEncryptionKey, remainingBlocks);
		EncryptedPositionMap encryptedPositionMap = encryptionManager.encryptPositionMap(newEncryptionKey,
				updatedPositionMap);
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

			Response response;
			if (oramContext.isDistributedKey()) {
				response = serviceProxy.invokeOrdered(serializedRequest, password.getBytes());
			} else {
				response = serviceProxy.invokeOrdered(serializedRequest);
			}
			if (response == null || response.getPlainData() == null) {
				return false;
			}
			Status status = Status.getStatus(response.getPlainData()[0]);
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
			List<Integer> toAdd = ORAMUtils.computePathLocationsList(i, oramContext.getTreeHeight());
			if(toAdd.contains(location)) {
				boolean possible = true;
				for (int j = bucketId-1; j >= 0; j--){
					if(myPath.contains(toAdd.get(j))) {
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

	private Stash mergeStashesAndPaths(Map<Integer, Stash> stashes, Map<Integer, Bucket[]> paths,
									   PositionMap mergedPositionMap) {
		Map<Integer, Block> recentBlocks = new HashMap<>();

		mergeStashes(recentBlocks, stashes, mergedPositionMap);
		mergePaths(recentBlocks, paths, mergedPositionMap);

		Stash mergedStash = new Stash(oramContext.getBlockSize());
		for (Block recentBlock : recentBlocks.values()) {
			mergedStash.putBlock(recentBlock);
		}
		return mergedStash;
	}

	private void mergePaths(Map<Integer, Block> recentBlocks, Map<Integer, Bucket[]> paths,
							PositionMap mergedPositionMap) {
		for (Bucket[] versions : paths.values()) {
			for (Bucket bucket : versions) {
				if (bucket == null)
					continue;
				for (Block block : bucket.readBucket()) {
					if (block == null)
						continue;
					int blockAddress = block.getAddress();
					int blockVersionId = block.getVersionId();
					if (blockVersionId == mergedPositionMap.getVersionIdAt(blockAddress)) {
						recentBlocks.put(blockAddress, block);
					}
				}
			}
		}
	}

	private void mergeStashes(Map<Integer, Block> recentBlocks, Map<Integer, Stash> stashes,
							  PositionMap mergedPositionMap) {
		Map<Integer, List<Block>> blocksToMerge = new HashMap<>();
		for (Map.Entry<Integer, Stash> entry : stashes.entrySet()) {
			blocksToMerge.put(entry.getKey(), entry.getValue().getBlocks());
		}
		selectRecentBlocks(recentBlocks, mergedPositionMap, blocksToMerge);
	}

	private void selectRecentBlocks(Map<Integer, Block> recentBlocks, PositionMap mergedPositionMap,
									Map<Integer, List<Block>> blocksToMerge) {
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
			Response response = serviceProxy.invokeOrderedHashed(serializedRequest);
			if (response == null || response.getPlainData() == null)
				return null;

			return encryptionManager.decryptStashesAndPathsResponse(oramContext, response);
		} catch (SecretSharingException e) {
			return null;
		}
	}
	protected abstract PositionMap updatePositionMap(Operation op, PositionMap mergedPositionMap, boolean isRealAccess, int address,
													 int newPathId, int newVersionId);

	protected abstract PositionMap mergePositionMaps(PositionMaps oldPositionMaps);

	protected abstract PositionMaps getPositionMaps();
}
