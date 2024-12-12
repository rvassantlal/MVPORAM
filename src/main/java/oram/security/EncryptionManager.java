package oram.security;

import oram.client.structure.*;
import oram.server.structure.*;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class EncryptionManager {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private final Logger measurementLogger = LoggerFactory.getLogger("measurement");
	private final EncryptionAbstraction encryptionAbstraction;
	private final SecureRandom rndGenerator;

	public EncryptionManager() {
		this.rndGenerator = new SecureRandom("oram".getBytes());
		this.encryptionAbstraction = new EncryptionAbstraction();
	}

	public PositionMaps decryptPositionMaps(byte[] serializedEncryptedPositionMaps) {
		EncryptedPositionMaps encryptedPositionMaps = new EncryptedPositionMaps();
		int offset = encryptedPositionMaps.readExternal(serializedEncryptedPositionMaps, 0);
		if (offset != serializedEncryptedPositionMaps.length) {
			logger.error("Failed to deserialize encrypted position maps");
			return null;
		}

		return decryptPositionMaps(encryptedPositionMaps);
	}

	public String generatePassword() {
		return ORAMUtils.generateRandomPassword(rndGenerator);
	}

	public void createSecretKey(String password) {
		encryptionAbstraction.createSecretKey(password.toCharArray());
	}

	public PositionMaps decryptPositionMaps(EncryptedPositionMaps encryptedPositionMaps) {
		Map<Integer, EncryptedPathMap> encryptedPMs = encryptedPositionMaps.getEncryptedPathMaps();
		Map<Integer, PathMap> pathMaps = new HashMap<>(encryptedPMs.size());
		for (Map.Entry<Integer, EncryptedPathMap> entry : encryptedPMs.entrySet()) {
			pathMaps.put(entry.getKey(), decryptPathMap(entry.getValue()));
		}

		return new PositionMaps(encryptedPositionMaps.getNewVersionId(), pathMaps);

	}

	public StashesAndPaths decryptStashesAndPaths(ORAMContext oramContext, byte[] serializedEncryptedStashesAndPaths) {
		EncryptedStashesAndPaths encryptedStashesAndPaths = new EncryptedStashesAndPaths(oramContext);
		int offset = encryptedStashesAndPaths.readExternal(serializedEncryptedStashesAndPaths, 0);
		if (offset != serializedEncryptedStashesAndPaths.length) {
			logger.error("Failed to deserialize encrypted stashes and paths");
			return null;
		}

		return decryptStashesAndPaths(oramContext, encryptedStashesAndPaths);
	}

	public StashesAndPaths decryptStashesAndPaths(ORAMContext oramContext,
												  EncryptedStashesAndPaths encryptedStashesAndPaths) {
		Map<Integer, Stash> stashes = decryptStashes(oramContext.getBlockSize(), encryptedStashesAndPaths.getEncryptedStashes());
		Bucket[] paths = decryptPaths(oramContext, encryptedStashesAndPaths.getPaths());
		return new StashesAndPaths(stashes, paths);
	}

	private Map<Integer, Stash> decryptStashes(int blockSize, Map<Integer, EncryptedStash> encryptedStashes) {
		Map<Integer, Stash> stashes = new HashMap<>(encryptedStashes.size());
		measurementLogger.info("M-receivedStashes: {}", encryptedStashes.size());
		long nBlocks = 0;
		for (Map.Entry<Integer, EncryptedStash> entry : encryptedStashes.entrySet()) {
			Stash stash = decryptStash(blockSize, entry.getValue());
			stashes.put(entry.getKey(), stash);
			nBlocks += stash.getBlocks().size();
		}
		measurementLogger.info("M-receivedStashBlocks: {}", nBlocks);

		return stashes;
	}

	private Bucket[] decryptPaths(ORAMContext oramContext, EncryptedBucket[] encryptedPaths) {
		Bucket[] paths = new Bucket[encryptedPaths.length];
		measurementLogger.info("M-receivedPathSize: {}", encryptedPaths.length);
		for (int i = 0; i < encryptedPaths.length; i++) {
			paths[i] = decryptBucket(oramContext, encryptedPaths[i]);
		}
		return paths;
	}

	public EncryptedPathMap encryptPathMap(PathMap pathMap) {
		int dataSize = pathMap.getSerializedSize();
		byte[] serializedPathMap = new byte[dataSize];
		int offset = pathMap.writeExternal(serializedPathMap, 0);
		if (offset != dataSize) {
			logger.error("Failed to serialize path map");
			return null;
		}

		byte[] encryptedPathMap = encryptionAbstraction.encrypt(serializedPathMap);
		return new EncryptedPathMap(encryptedPathMap);
	}

	public EncryptedPositionMap encryptPositionMap(PositionMap positionMap) {
		int dataSize = positionMap.getSerializedSize();
		byte[] serializedPositionMap = new byte[dataSize];
		int offset = positionMap.writeExternal(serializedPositionMap, 0);
		if (offset != dataSize) {
			logger.error("Failed to serialize position map");
			return null;
		}

		byte[] encryptedPositionMap = encryptionAbstraction.encrypt(serializedPositionMap);
		//logger.info("Position map size: {} bytes", serializedPositionMap.length);
		//logger.info("Encrypted position map size: {} bytes", encryptedPositionMap.length);
		return new EncryptedPositionMap(encryptedPositionMap);
	}

	public PathMap decryptPathMap(EncryptedPathMap encryptedPathMap) {
		byte[] serializedPathMap = encryptionAbstraction.decrypt(encryptedPathMap.getEncryptedPathMap());
		PathMap deserializedPathMap = new PathMap();
		int offset = deserializedPathMap.readExternal(serializedPathMap, 0);
		if (offset != serializedPathMap.length) {
			logger.error("Failed to deserialize path map");
			return null;
		}
		return deserializedPathMap;
	}

	public PositionMap decryptPositionMap(EncryptedPositionMap encryptedPositionMap) {
		byte[] serializedPositionMap = encryptionAbstraction.decrypt(encryptedPositionMap.getEncryptedPositionMap());
		PositionMap deserializedPositionMap = new PositionMap();
		int offset = deserializedPositionMap.readExternal(serializedPositionMap, 0);
		if (offset != serializedPositionMap.length) {
			logger.error("Failed to deserialize position map");
			return null;
		}
		return deserializedPositionMap;
	}

	public EncryptedStash encryptStash(Stash stash) {
		measurementLogger.info("M-sentStashBlocks: {}", stash.getBlocks().size());
		int dataSize = stash.getSerializedSize();
		byte[] serializedStash = new byte[dataSize];
		stash.writeExternal(serializedStash, 0);
		return new EncryptedStash(encryptionAbstraction.encrypt(serializedStash));
	}

	public Stash decryptStash(int blockSize, EncryptedStash encryptedStash) {
		byte[] serializedStash = encryptionAbstraction.decrypt(encryptedStash.getEncryptedStash());
		Stash deserializedStash = new Stash(blockSize);
		deserializedStash.readExternal(serializedStash, 0);
		return deserializedStash;
	}

	public EncryptedBucket encryptBucket(ORAMContext oramContext, Bucket bucket) {
		prepareBucket(oramContext, bucket);
		Block[] bucketContents = bucket.readBucket();
		byte[][] encryptedBlocks = new byte[bucketContents.length][];
		for (int i = 0; i < bucketContents.length; i++) {
			Block block = bucketContents[i];
			byte[] serializedBlock = new byte[block.getSerializedSize()];
			block.writeExternal(serializedBlock, 0);
			encryptedBlocks[i] = encryptionAbstraction.encrypt(serializedBlock);
		}
		return new EncryptedBucket(encryptedBlocks, bucket.getLocation());
	}

	public Bucket decryptBucket(ORAMContext oramContext, EncryptedBucket encryptedBucket) {
		if (encryptedBucket == null)
			return null;
		byte[][] blocks = encryptedBucket.getBlocks();
		Bucket newBucket = new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize(), encryptedBucket.getLocation());

		for (byte[] block : blocks) {
			byte[] serializedBlock = encryptionAbstraction.decrypt(block);
			Block deserializedBlock = new Block(oramContext.getBlockSize());
			deserializedBlock.readExternal(serializedBlock, 0);
			if (deserializedBlock.getAddress() != ORAMUtils.DUMMY_ADDRESS
					&& !Arrays.equals(deserializedBlock.getContent(), ORAMUtils.DUMMY_BLOCK)) {
				newBucket.putBlock(deserializedBlock);
			}
		}
		return newBucket;
	}

	public DebugSnapshot decryptDebugSnapshot(ORAMContext context, byte[] plainData) {
		EncryptedDebugSnapshot encryptedDebugSnapshot = new EncryptedDebugSnapshot(context.getBucketSize());
		int offset = encryptedDebugSnapshot.readExternal(plainData, 0);
		if (offset != plainData.length) {
			logger.error("Failed to deserialize encrypted debug snapshot");
			return null;
		}
		BucketHolder[] encryptedTree = encryptedDebugSnapshot.getTree();
		Map<Integer, EncryptedStash> encryptedStashes = encryptedDebugSnapshot.getStashes();

		ArrayList<Bucket>[] tree = new ArrayList[encryptedTree.length];
		for (int i = 0; i < encryptedTree.length; i++) {
			BucketHolder bucketHolder = encryptedTree[i];
			ArrayList<Bucket> buckets = new ArrayList<>(bucketHolder.getOutstandingBucketsVersions().size());
			for (BucketSnapshot encryptedBucket : bucketHolder.getOutstandingBucketsVersions()) {
				Bucket bucket = decryptBucket(context, encryptedBucket.getBucket());
				buckets.add(bucket);
			}
			tree[i] = buckets;
		}

		Map<Integer, Stash> stashes = new HashMap<>(encryptedStashes.size());
		for (Map.Entry<Integer, EncryptedStash> entry : encryptedStashes.entrySet()) {
			stashes.put(entry.getKey(), decryptStash(context.getBlockSize(), entry.getValue()));
		}

		return new DebugSnapshot(tree, stashes);
	}

	public Map<Integer, EncryptedBucket> encryptPath(ORAMContext oramContext, Map<Integer, Bucket> path) {
		Map<Integer, EncryptedBucket> encryptedPath = new HashMap<>(path.size());
		measurementLogger.info("M-sentPathSize: {}", path.size());
		for (Map.Entry<Integer, Bucket> entry : path.entrySet()) {
			Bucket bucket = entry.getValue();
			EncryptedBucket encryptedBucket = encryptBucket(oramContext, bucket);
			encryptedPath.put(entry.getKey(), encryptedBucket);
		}
		return encryptedPath;
	}

	private void prepareBucket(ORAMContext oramContext, Bucket bucket) {
		Block[] blocks = bucket.readBucket();
		for (int i = 0; i < blocks.length; i++) {
			if (blocks[i] == null) {
				blocks[i] = new Block(oramContext.getBlockSize(), ORAMUtils.DUMMY_ADDRESS, ORAMUtils.DUMMY_VERSION,
						ORAMUtils.DUMMY_BLOCK);
			}
		}
	}
}
