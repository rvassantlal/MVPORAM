package oram.security;

import oram.client.structure.*;
import oram.server.structure.*;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class EncryptionManager {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private final EncryptionAbstraction encryptionAbstraction;
	private static final byte[] salt = {0x56, 0x1a, 0x7e, 0x23, (byte) 0xb3, 0x21, 0x12, (byte) 0xf6,
			(byte) 0xe1, 0x4d, 0x58, (byte) 0xd9, 0x0a, 0x59, (byte) 0xee, (byte) 0xe5, 0x3b, 0x61, 0x78, 0x27, 0x1e,
			(byte) 0xad, 0x52, 0x41, 0x2c, 0x4b, (byte) 0xb6, 0x7b, (byte) 0xcd, 0x3a, (byte) 0xe9, (byte) 0x9c};

	public EncryptionManager() {
		this.encryptionAbstraction = new EncryptionAbstraction();
	}

	public SecretKey createSecretKey(char[] password) {
		PBEKeySpec keySpec = new PBEKeySpec(password, salt, 65536,256);
		SecretKeyFactory kf;
		try {
			kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			return new SecretKeySpec(kf.generateSecret(keySpec).getEncoded(),"AES");
		} catch (Exception e) {
			logger.error("Failed to create secret key", e);
			return null;
		}
	}

	public PositionMaps decryptPositionMaps(SecretKey[] decryptionKeys, byte[] serializedEncryptedPositionMaps) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedEncryptedPositionMaps);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			EncryptedPositionMaps encryptedPositionMaps = new EncryptedPositionMaps();
			encryptedPositionMaps.readExternal(in);
			return decryptPositionMaps(decryptionKeys, encryptedPositionMaps);
		} catch (IOException | ClassNotFoundException e) {
			logger.error("Failed to decrypt position map", e);
			return null;
		}
	}


	public PositionMaps decryptPositionMaps(SecretKey[] decryptionKeys, EncryptedPositionMaps encryptedPositionMaps) {
		Map<Integer,EncryptedPositionMap> encryptedPMs = encryptedPositionMaps.getEncryptedPositionMaps();
		Map<Integer,PositionMap> positionMaps = new HashMap<>(encryptedPMs.size());
		AtomicInteger i = new AtomicInteger();
		encryptedPMs.keySet().stream().sorted().forEach(index ->
			positionMaps.put(index,decryptPositionMap(decryptionKeys[i.getAndIncrement()], encryptedPMs.get(index)))
		);
		return new PositionMaps(encryptedPositionMaps.getNewVersionId(),
				encryptedPositionMaps.getOutstandingVersionIds(), positionMaps);

	}

	public StashesAndPaths decryptStashesAndPaths(Map<Integer, SecretKey> decryptionKeys, ORAMContext oramContext,
												  byte[] serializedEncryptedStashesAndPaths) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedEncryptedStashesAndPaths);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			EncryptedStashesAndPaths encryptedStashesAndPaths = new EncryptedStashesAndPaths(oramContext);
			encryptedStashesAndPaths.readExternal(in);

			return decryptStashesAndPaths(decryptionKeys, oramContext, encryptedStashesAndPaths);
		} catch (IOException | ClassNotFoundException e) {
			logger.error("Failed to decrypt stashes and paths", e);
			return null;
		}
	}

	public StashesAndPaths decryptStashesAndPaths(Map<Integer, SecretKey> decryptionKeys, ORAMContext oramContext,
												  EncryptedStashesAndPaths encryptedStashesAndPaths) {
		Map<Integer, Stash> stashes = decryptStashes(decryptionKeys, oramContext.getBlockSize(),
				encryptedStashesAndPaths.getEncryptedStashes());
		Map<Integer, Bucket[]> paths = decryptPaths(decryptionKeys, oramContext, encryptedStashesAndPaths.getPaths());
		return new StashesAndPaths(stashes, paths);
	}

	private Map<Integer, Stash> decryptStashes(Map<Integer, SecretKey> decryptionKeys, int blockSize,
											   Map<Integer, EncryptedStash> encryptedStashes) {
		Map<Integer, Stash> stashes = new HashMap<>(encryptedStashes.size());
		for (Map.Entry<Integer, EncryptedStash> entry : encryptedStashes.entrySet()) {
			int versionId = entry.getKey();
			SecretKey decryptionKey = decryptionKeys.get(versionId);
			stashes.put(entry.getKey(), decryptStash(decryptionKey, blockSize, entry.getValue()));
		}
		return stashes;
	}

	private Map<Integer, Bucket[]> decryptPaths(Map<Integer, SecretKey> decryptionKeys, ORAMContext oramContext,
												Map<Integer, EncryptedBucket[]> encryptedPaths) {
		Map<Integer, Bucket[]> paths = new HashMap<>(encryptedPaths.size());
		for (Map.Entry<Integer, EncryptedBucket[]> entry : encryptedPaths.entrySet()) {
			EncryptedBucket[] encryptedBuckets = entry.getValue();
			Bucket[] buckets = new Bucket[encryptedBuckets.length];
			SecretKey decryptionKey = decryptionKeys.get(entry.getKey());
			for (int i = 0; i < encryptedBuckets.length; i++) {
				buckets[i] = decryptBucket(decryptionKey, oramContext, encryptedBuckets[i]);
			}
			paths.put(entry.getKey(), buckets);
		}
		return paths;
	}

	public EncryptedPositionMap encryptPositionMap(SecretKey encryptionKey, PositionMap positionMap) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			positionMap.writeExternal(out);
			out.flush();
			bos.flush();
			return new EncryptedPositionMap(encryptionAbstraction.encrypt(encryptionKey, bos.toByteArray()));
		} catch (IOException e) {
			logger.error("Failed to encrypt position map", e);
			return null;
		}
	}

	public PositionMap decryptPositionMap(SecretKey decryptionKey, EncryptedPositionMap encryptedPositionMap) {
		byte[] encryptedPositionMapContent = encryptedPositionMap.getEncryptedPositionMap();
		if(encryptedPositionMapContent == null)
			return null;
		byte[] serializedPositionMap = encryptionAbstraction.decrypt(decryptionKey, encryptedPositionMapContent);
		PositionMap deserializedPositionMap = new PositionMap();
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedPositionMap);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			deserializedPositionMap.readExternal(in);
		} catch (IOException e) {
			logger.error("Failed to decrypt position map", e);
			return null;
		}
		return deserializedPositionMap;
	}

	public EncryptedStash encryptStash(SecretKey encryptionKey, Stash stash) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			stash.writeExternal(out);
			out.flush();
			bos.flush();
			return new EncryptedStash(encryptionAbstraction.encrypt(encryptionKey, bos.toByteArray()));
		} catch (IOException e) {
			logger.error("Failed to encrypt stash", e);
			return null;
		}
	}

	public Stash decryptStash(SecretKey decryptionKey, int blockSize, EncryptedStash encryptedStash) {
		byte[] encryptedStashBytes = encryptedStash.getEncryptedStash();
		if(encryptedStashBytes == null){
			return null;
		}
		byte[] serializedStash = encryptionAbstraction.decrypt(decryptionKey, encryptedStashBytes);
		Stash deserializedStash = new Stash(blockSize);
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedStash);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			deserializedStash.readExternal(in);
		} catch (IOException | ClassNotFoundException e) {
			logger.error("Failed to decrypt stash", e);
			return null;
		}
		return deserializedStash;
	}

	public EncryptedBucket encryptBucket(SecretKey encryptionKey, ORAMContext oramContext, Bucket bucket) {
		prepareBucket(oramContext, bucket);
		Block[] bucketContents = bucket.readBucket();
		byte[][] encryptedBlocks = new byte[bucketContents.length][];
		for (int i = 0; i < bucketContents.length; i++) {
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				 ObjectOutputStream out = new ObjectOutputStream(bos)) {
				bucketContents[i].writeExternal(out);
				out.flush();
				bos.flush();
				encryptedBlocks[i] = encryptionAbstraction.encrypt(encryptionKey, bos.toByteArray());
			} catch (IOException e) {
				logger.error("Failed to encrypt bucket", e);
				return null;
			}
		}
		return new EncryptedBucket(encryptedBlocks);
	}

	public Bucket decryptBucket(SecretKey decryptionKey, ORAMContext oramContext, EncryptedBucket encryptedBucket) {
		byte[][] blocks = encryptedBucket.getBlocks();
		Bucket newBucket = new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize());

		for (byte[] block : blocks) {
			byte[] serializedBlock = encryptionAbstraction.decrypt(decryptionKey, block);
			Block deserializedBlock = new Block(oramContext.getBlockSize());
			try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedBlock);
				 ObjectInputStream in = new ObjectInputStream(bis)) {
				deserializedBlock.readExternal(in);
			} catch (IOException e) {
				logger.error("Failed to decrypt block", e);
				return null;
			}
			if (deserializedBlock.getAddress() != ORAMUtils.DUMMY_ADDRESS
					&& !Arrays.equals(deserializedBlock.getContent(), ORAMUtils.DUMMY_BLOCK)) {
				newBucket.putBlock(deserializedBlock);
			}
		}
		return newBucket;
	}

	public Map<Integer, EncryptedBucket> encryptPath(SecretKey encryptionKey, ORAMContext oramContext,
													 Map<Integer, Bucket> path) {
		Map<Integer, EncryptedBucket> encryptedPath = new HashMap<>(path.size());
		for (Map.Entry<Integer, Bucket> entry : path.entrySet()) {
			Bucket bucket = entry.getValue();
			EncryptedBucket encryptedBucket = encryptBucket(encryptionKey, oramContext, bucket);
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
