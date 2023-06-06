package oram.client;

import oram.client.structure.*;
import oram.server.structure.*;
import oram.security.EncryptionAbstraction;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class EncryptionManager {
	private final EncryptionAbstraction encryptionAbstraction;

	public EncryptionManager() {
		this.encryptionAbstraction = new EncryptionAbstraction("oram");
	}

	public PositionMap[] decryptPositionMaps(byte[] serializedEncryptedPositionMaps) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedEncryptedPositionMaps);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			int nPositionMaps = in.readInt();
			PositionMap[] positionMaps = new PositionMap[nPositionMaps];
			EncryptedPositionMap encryptedPositionMap;
			for (int i = 0; i < nPositionMaps; i++) {
				encryptedPositionMap = new EncryptedPositionMap();
				encryptedPositionMap.readExternal(in);
				positionMaps[i] = decryptPositionMap(encryptedPositionMap);
			}
			return positionMaps;
		} catch (IOException | ClassNotFoundException e) {
			return null;
		}
	}

	public StashesAndPaths decryptStashesAndPaths(ORAMContext oramContext, byte[] serializedEncryptedStashesAndPaths) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedEncryptedStashesAndPaths);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			EncryptedStashesAndPaths encryptedStashesAndPaths = new EncryptedStashesAndPaths(oramContext);
			encryptedStashesAndPaths.readExternal(in);

			Map<Double, Stash> stashes = decryptStashes(oramContext.getBlockSize(),
					encryptedStashesAndPaths.getEncryptedStashes());
			Map<Double, Bucket[]> paths = decryptPaths(oramContext.getBucketSize(), oramContext.getBlockSize(),
					encryptedStashesAndPaths.getPaths());
			return new StashesAndPaths(stashes, paths);
		} catch (IOException | ClassNotFoundException e) {
			return null;
		}
	}

	private Map<Double, Stash> decryptStashes(int blockSize, Map<Double, EncryptedStash> encryptedStashes) {
		Map<Double, Stash> stashes = new HashMap<>(encryptedStashes.size());
		for (Map.Entry<Double, EncryptedStash> entry : encryptedStashes.entrySet()) {
			stashes.put(entry.getKey(), decryptStash(blockSize, entry.getValue()));
		}
		return stashes;
	}

	private Map<Double, Bucket[]> decryptPaths(int bucketSize, int blockSize, Map<Double,
			EncryptedBucket[]> encryptedPaths) {
		Map<Double, Bucket[]> paths = new HashMap<>(encryptedPaths.size());
		for (Map.Entry<Double, EncryptedBucket[]> entry : encryptedPaths.entrySet()) {
			EncryptedBucket[] encryptedBuckets = entry.getValue();
			Bucket[] buckets = new Bucket[encryptedBuckets.length];
			for (int i = 0; i < encryptedBuckets.length; i++) {
				buckets[i] = decryptBucket(bucketSize, blockSize, encryptedBuckets[i]);
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
			return null;
		}
	}

	public PositionMap decryptPositionMap(EncryptedPositionMap encryptedPositionMap) {
		byte[] serializedPositionMap = encryptionAbstraction.decrypt(encryptedPositionMap.getEncryptedPositionMap());
		PositionMap deserializedPositionMap = new PositionMap();
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedPositionMap);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			deserializedPositionMap.readExternal(in);
		} catch (IOException e) {
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
			return null;
		}
	}

	public Stash decryptStash(int blockSize, EncryptedStash encryptedStash) {
		byte[] serializedStash = encryptionAbstraction.decrypt(encryptedStash.getEncryptedStash());
		Stash deserializedStash = new Stash(blockSize);
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedStash);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			deserializedStash.readExternal(in);
		} catch (IOException | ClassNotFoundException e) {
			return null;
		}
		return deserializedStash;
	}

	public EncryptedBucket encryptBucket(int blockSize, Bucket bucket) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			Block[] bucketContents = bucket.readBucket();
			byte[][] encryptedBlocks = new byte[bucketContents.length][];
			//TODO fill bucket with dummy blocks
			for (int i = 0; i < bucketContents.length; i++) {
				bucketContents[i].writeExternal(out);
				out.flush();
				bos.flush();
				byte[] encryptedBlock = encryptionAbstraction.encrypt(bos.toByteArray());
				encryptedBlocks[i] = encryptedBlock;
				bos.reset();
			}
			return new EncryptedBucket(blockSize, encryptedBlocks);
		} catch (IOException e) {
			return null;
		}
	}

	public Bucket decryptBucket(int bucketSize, int blockSize, EncryptedBucket encryptedBucket) {
		byte[][] blocks = encryptedBucket.getBlocks();
		Bucket newBucket = new Bucket(bucketSize, blockSize);
		//TODO remove dummy blocks
		for (byte[] block : blocks) {
			byte[] serializedBlock = encryptionAbstraction.decrypt(block);
			Block deserializedBlock = new Block(blockSize);
			try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedBlock);
				 ObjectInputStream in = new ObjectInputStream(bis)) {
				deserializedBlock.readExternal(in);
			} catch (IOException | ClassNotFoundException e) {
				return null;
			}
			newBucket.putBlock(deserializedBlock);
		}
		return newBucket;
	}

	public Map<Integer, EncryptedBucket> encryptPath(int blockSize, Map<Integer, Bucket> path) {
		Map<Integer, EncryptedBucket> encryptedPath = new HashMap<>(path.size());
		for (Map.Entry<Integer, Bucket> entry : path.entrySet()) {
			EncryptedBucket encryptedBucket = encryptBucket(blockSize, entry.getValue());
			encryptedPath.put(entry.getKey(), encryptedBucket);
		}
		return encryptedPath;
	}
}
