package oram.client;

import oram.client.structure.Bucket;
import oram.client.structure.PositionMap;
import oram.client.structure.Stash;
import oram.client.structure.StashesAndPaths;
import oram.server.structure.*;
import security.EncryptionAbstraction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
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

			Map<Double, Stash> stashes = decryptStashes(encryptedStashesAndPaths.getEncryptedStashes());
			Map<Double, Bucket[]> paths = decryptPaths(encryptedStashesAndPaths.getPaths());
			return new StashesAndPaths(stashes, paths);
		} catch (IOException | ClassNotFoundException e) {
			return null;
		}
	}

	private Map<Double, Stash> decryptStashes(List<EncryptedStash> encryptedStashes) {
		Map<Double, Stash> stashes = new HashMap<>(encryptedStashes.size());
		int i = 0;
		for (EncryptedStash encryptedStash : encryptedStashes) {
			stashes.put(encryptedStash.getVersionId(), decryptStash(encryptedStash));
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
		//TODO
		return null;
	}

	public PositionMap decryptPositionMap(EncryptedPositionMap encryptedPositionMap) {
		byte[] serializedPositionMap = encryptionAbstraction.decrypt(encryptedPositionMap.getEncryptedPositionMap());
		//TODO
		return new PositionMap();
	}

	public EncryptedStash encryptStash(Stash stash) {
		//TODO
		return null;
	}

	public Stash decryptStash(EncryptedStash encryptedStash) {
		//TODO don't forget stash version
		return null;
	}

	public EncryptedBucket encryptBucket(Bucket bucket) {
		//TODO
		return null;
	}

	public Bucket decryptBucket(EncryptedBucket bucket) {
		//TODO
		return null;
	}
}
