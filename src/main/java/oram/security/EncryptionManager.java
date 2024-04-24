package oram.security;

import oram.client.structure.*;
import oram.server.structure.*;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.SecureRandom;
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
		EncryptedPositionMaps encryptedPositionMaps =
				ORAMUtils.deserializeEncryptedPositionMaps(serializedEncryptedPositionMaps);
		return decryptPositionMaps(encryptedPositionMaps);
	}

	public String generatePassword() {
		return ORAMUtils.generateRandomPassword(rndGenerator);
	}

	public void createSecretKey(String password) {
		encryptionAbstraction.createSecretKey(password.toCharArray());
	}

	public PositionMaps decryptPositionMaps(EncryptedPositionMaps encryptedPositionMaps) {
		Map<Integer, EncryptedPositionMap> encryptedPMs = encryptedPositionMaps.getEncryptedPositionMaps();
		Map<Integer, PositionMap> positionMaps = new HashMap<>(encryptedPMs.size());
		for (Map.Entry<Integer, EncryptedPositionMap> entry : encryptedPMs.entrySet()) {
			positionMaps.put(entry.getKey(), decryptPositionMap(entry.getValue()));
		}
		return new PositionMaps(encryptedPositionMaps.getNewVersionId(), positionMaps);

	}

	public StashesAndPaths decryptStashesAndPaths(ORAMContext oramContext, byte[] serializedEncryptedStashesAndPaths) {

		EncryptedStashesAndPaths encryptedStashesAndPaths = ORAMUtils.deserializeEncryptedPathAndStash(oramContext,
				serializedEncryptedStashesAndPaths);

		return decryptStashesAndPaths(oramContext, encryptedStashesAndPaths);
	}

	public StashesAndPaths decryptStashesAndPaths(ORAMContext oramContext,
												  EncryptedStashesAndPaths encryptedStashesAndPaths) {
		Stash[] stashes = decryptStashes(oramContext.getBlockSize(), encryptedStashesAndPaths.getEncryptedStashes());
		Bucket[] paths = decryptPaths(oramContext, encryptedStashesAndPaths.getPaths());
		return new StashesAndPaths(stashes, paths);
	}

	private Stash[] decryptStashes(int blockSize, EncryptedStash[] encryptedStashes) {
		Stash[] stashes = new Stash[encryptedStashes.length];
		measurementLogger.info("M-receivedStashes: {}", stashes.length);
		long nBlocks = 0;
		for (int i = 0; i < encryptedStashes.length; i++) {
			stashes[i] = decryptStash(blockSize, encryptedStashes[i]);
			nBlocks += stashes[i].getBlocks().size();
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

	public EncryptedPositionMap encryptPositionMap(PositionMap positionMap) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 DataOutputStream out = new DataOutputStream(bos)) {
			positionMap.writeExternal(out);
			out.flush();
			bos.flush();
			byte[] serializedPositionMap = bos.toByteArray();
			byte[] encryptedPositionMap = encryptionAbstraction.encrypt(serializedPositionMap);
			//logger.info("Position map size: {} bytes", serializedPositionMap.length);
			//logger.info("Encrypted position map size: {} bytes", encryptedPositionMap.length);
			return new EncryptedPositionMap(encryptedPositionMap);
		} catch (IOException e) {
			logger.error("Failed to encrypt position map", e);
			return null;
		}
	}

	public PositionMap decryptPositionMap(EncryptedPositionMap encryptedPositionMap) {
		byte[] serializedPositionMap = encryptionAbstraction.decrypt(encryptedPositionMap.getEncryptedPositionMap());
		PositionMap deserializedPositionMap = new PositionMap();
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedPositionMap);
			 DataInputStream in = new DataInputStream(bis)) {
			deserializedPositionMap.readExternal(in);
		} catch (IOException e) {
			logger.error("Failed to decrypt position map", e);
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
		return new EncryptedBucket(encryptedBlocks);
	}

	public Bucket decryptBucket(ORAMContext oramContext, EncryptedBucket encryptedBucket) {
		if (encryptedBucket == null)
			return null;
		byte[][] blocks = encryptedBucket.getBlocks();
		Bucket newBucket = new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize());

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
