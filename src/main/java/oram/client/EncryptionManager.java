package oram.client;

import oram.client.structure.*;
import oram.server.structure.*;
import security.EncryptionAbstraction;

import java.io.*;
import java.util.HashMap;
import java.util.List;
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

			Map<Double, Stash> stashes = decryptStashes(oramContext.getBlockSize(),encryptedStashesAndPaths.getEncryptedStashes());
			Map<Double, Bucket[]> paths = decryptPaths(encryptedStashesAndPaths.getPaths());
			return new StashesAndPaths(stashes, paths);
		} catch (IOException | ClassNotFoundException e) {
			return null;
		}
	}

	private Map<Double, Stash> decryptStashes(int blockSize,List<EncryptedStash> encryptedStashes) {
		Map<Double, Stash> stashes = new HashMap<>(encryptedStashes.size());
		int i = 0;
		for (EncryptedStash encryptedStash : encryptedStashes) {
			stashes.put(encryptedStash.getVersionId(), decryptStash(blockSize,encryptedStash));
		}
		return stashes;
	}

	private Map<Double, Bucket[]> decryptPaths(Map<Double, EncryptedBucket[]> encryptedPaths) {
		Map<Double, Bucket[]> paths = new HashMap<>(encryptedPaths.size());
		for (Map.Entry<Double, EncryptedBucket[]> entry : encryptedPaths.entrySet()) {
			EncryptedBucket[] encryptedBuckets = entry.getValue();
			Bucket[] buckets = new Bucket[encryptedBuckets.length];
			for (int i = 0; i < encryptedBuckets.length; i++) {
				buckets[i] = decryptBucket(encryptedBuckets[i]);
			}
			paths.put(entry.getKey(), buckets);
		}
		return paths;
	}

	public EncryptedPositionMap encryptPositionMap(PositionMap positionMap) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			positionMap.writeExternal(out);
			return new EncryptedPositionMap(encryptionAbstraction.encrypt(bos.toByteArray()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public PositionMap decryptPositionMap(EncryptedPositionMap encryptedPositionMap) {
		byte[] serializedPositionMap = encryptionAbstraction.decrypt(encryptedPositionMap.getEncryptedPositionMap());
		PositionMap deserializedPositionMap = new PositionMap();
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedPositionMap);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			deserializedPositionMap.readExternal(in);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return deserializedPositionMap;
	}

	public EncryptedStash encryptStash(double versionId,Stash stash) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			stash.writeExternal(out);
			return new EncryptedStash(versionId,
					encryptionAbstraction.encrypt(bos.toByteArray()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Stash decryptStash(int blockSize, EncryptedStash encryptedStash) {
		byte[] serializedStash = encryptionAbstraction.decrypt(encryptedStash.getEncryptedStash());
		Stash deserializedStash = new Stash(blockSize, encryptedStash.getVersionId());
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedStash);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			deserializedStash.readExternal(in);
		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		return deserializedStash;
	}

	public EncryptedBucket encryptBucket(Bucket bucket) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			Block[] bucketContents = bucket.readBucket();
			byte[][] encryptedBlocks = new byte[bucketContents.length][];
			int i=0;
			for (Block block : bucketContents) {
				block.writeExternal(out);
				byte[] encryptedBlock = encryptionAbstraction.encrypt(bos.toByteArray());
				encryptedBlocks[i++]=encryptedBlock;
				bos.reset();
			}
			return new EncryptedBucket(bucket.getBlockSize(),encryptedBlocks);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Bucket decryptBucket(EncryptedBucket encryptedBucket) {
		byte[][] blocks = encryptedBucket.getBlocks();
		Bucket newBucket = new Bucket(blocks.length, encryptedBucket.getBlockSize());
		for (byte[] block : blocks) {
			byte[] serializedBlock = encryptionAbstraction.decrypt(block);
			Block deserializedBlock = new Block(encryptedBucket.getBlockSize());
			try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedBlock);
				 ObjectInputStream in = new ObjectInputStream(bis)) {
				deserializedBlock.readExternal(in);
			} catch (IOException | ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
			newBucket.putBlock(deserializedBlock);
		}
		return newBucket;
	}
}
