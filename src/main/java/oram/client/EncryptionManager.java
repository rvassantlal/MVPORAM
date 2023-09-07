package oram.client;

import oram.client.structure.*;
import oram.security.EncryptionAbstraction;
import oram.server.structure.*;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class EncryptionManager {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private final EncryptionAbstraction encryptionAbstraction;

	public EncryptionManager() {
		this.encryptionAbstraction = new EncryptionAbstraction("oram");
	}

	public PositionMaps decryptPositionMaps(byte[] serializedEncryptedPositionMaps) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedEncryptedPositionMaps);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			EncryptedPositionMaps encryptedPositionMaps = new EncryptedPositionMaps();
			encryptedPositionMaps.readExternal(in);
			return decryptPositionMaps(encryptedPositionMaps);
		} catch (IOException | ClassNotFoundException e) {
			logger.error("Failed to decrypt position map", e);
			return null;
		}
	}

	public PositionMaps decryptPositionMaps(EncryptedPositionMaps encryptedPositionMaps) {
		EncryptedPositionMap[] encryptedPMs = encryptedPositionMaps.getEncryptedPositionMaps();
		PositionMap[] positionMaps = new PositionMap[encryptedPMs.length];
		for (int i = 0; i < encryptedPMs.length; i++) {
			positionMaps[i] = decryptPositionMap(encryptedPMs[i]);
		}
		return new PositionMaps(encryptedPositionMaps.getNewVersionId(),
				encryptedPositionMaps.getOutstandingVersionIds(), positionMaps);

	}

	public StashesAndPaths decryptStashesAndPaths(ORAMContext oramContext, byte[] serializedEncryptedStashesAndPaths) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedEncryptedStashesAndPaths);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			EncryptedStashesAndPaths encryptedStashesAndPaths = new EncryptedStashesAndPaths(oramContext);
			encryptedStashesAndPaths.readExternal(in);

			return decryptStashesAndPaths(oramContext, encryptedStashesAndPaths);
		} catch (IOException | ClassNotFoundException e) {
			logger.error("Failed to decrypt stashes and paths", e);
			return null;
		}
	}

	public StashesAndPaths decryptStashesAndPaths(ORAMContext oramContext,
												  EncryptedStashesAndPaths encryptedStashesAndPaths) {
		Map<Integer, Stash> stashes = decryptStashes(oramContext.getBlockSize(),
				encryptedStashesAndPaths.getEncryptedStashes());
		Map<Integer, Bucket[]> paths = decryptPaths(oramContext, encryptedStashesAndPaths.getPaths());
		return new StashesAndPaths(stashes, paths);
	}

	private Map<Integer, Stash> decryptStashes(int blockSize, Map<Integer, EncryptedStash> encryptedStashes) {
		Map<Integer, Stash> stashes = new HashMap<>(encryptedStashes.size());
		for (Map.Entry<Integer, EncryptedStash> entry : encryptedStashes.entrySet()) {
			stashes.put(entry.getKey(), decryptStash(blockSize, entry.getValue()));
		}
		return stashes;
	}

	private Map<Integer, Bucket[]> decryptPaths(ORAMContext oramContext, Map<Integer, EncryptedBucket[]> encryptedPaths) {
		Map<Integer, Bucket[]> paths = new HashMap<>(encryptedPaths.size());
		for (Map.Entry<Integer, EncryptedBucket[]> entry : encryptedPaths.entrySet()) {
			EncryptedBucket[] encryptedBuckets = entry.getValue();
			Bucket[] buckets = new Bucket[encryptedBuckets.length];
			for (int i = 0; i < encryptedBuckets.length; i++) {
				buckets[i] = decryptBucket(oramContext, encryptedBuckets[i]);
			}
			paths.put(entry.getKey(), buckets);
		}
		return paths;
	}

	public EncryptedPositionMap encryptPositionMap(PositionMap positionMap) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			positionMap.writeExternal(out);
			out.flush();
			bos.flush();
			return new EncryptedPositionMap(encryptionAbstraction.encrypt(bos.toByteArray()));
		} catch (IOException e) {
			logger.error("Failed to encrypt position map", e);
			return null;
		}
	}

	public PositionMap decryptPositionMap(EncryptedPositionMap encryptedPositionMap) {
		byte[] encryptedPositionMapContent = encryptedPositionMap.getEncryptedPositionMap();
		if(encryptedPositionMapContent == null)
			return null;
		byte[] serializedPositionMap = encryptionAbstraction.decrypt(encryptedPositionMapContent);
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

	public EncryptedStash encryptStash(Stash stash) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			stash.writeExternal(out);
			out.flush();
			bos.flush();
			return new EncryptedStash(encryptionAbstraction.encrypt(bos.toByteArray()));
		} catch (IOException e) {
			logger.error("Failed to encrypt stash", e);
			return null;
		}
	}

	public Stash decryptStash(int blockSize, EncryptedStash encryptedStash) {
		byte[] encryptedStashBytes = encryptedStash.getEncryptedStash();
		if(encryptedStashBytes == null){
			return null;
		}
		byte[] serializedStash = encryptionAbstraction.decrypt(encryptedStashBytes);
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

	public EncryptedBucket encryptBucket(ORAMContext oramContext, Bucket bucket) {
		prepareBucket(oramContext, bucket);
		Block[] bucketContents = bucket.readBucket();
		byte[][] encryptedBlocks = new byte[bucketContents.length][];
		for (int i = 0; i < bucketContents.length; i++) {
			try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				 ObjectOutputStream out = new ObjectOutputStream(bos)) {
				bucketContents[i].writeExternal(out);
				out.flush();
				bos.flush();
				encryptedBlocks[i] = encryptionAbstraction.encrypt(bos.toByteArray());
			} catch (IOException e) {
				logger.error("Failed to encrypt bucket", e);
				return null;
			}
		}
		return new EncryptedBucket(encryptedBlocks);
	}

	public Bucket decryptBucket(ORAMContext oramContext, EncryptedBucket encryptedBucket) {
		byte[][] blocks = encryptedBucket.getBlocks();
		Bucket newBucket = new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize());

		for (byte[] block : blocks) {
			byte[] serializedBlock = encryptionAbstraction.decrypt(block);
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

	public Map<Integer, EncryptedBucket> encryptPath(ORAMContext oramContext, Map<Integer, Bucket> path) {
		Map<Integer, EncryptedBucket> encryptedPath = new HashMap<>(path.size());
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
