package oram.server.structure;

import oram.utils.ORAMContext;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;

public class EncryptedStashesAndPaths implements Externalizable {
	private ORAMContext oramContext;
	private Map<Integer, EncryptedStash> encryptedStashes;
	private Map<Integer, EncryptedBucket[]> paths;
	private Map<Integer, Set<Integer>> versionPaths;
	private int ints = 0;
	private int buckets = 0;

	public EncryptedStashesAndPaths() {
	}

	public EncryptedStashesAndPaths(ORAMContext oramContext) {
		this.oramContext = oramContext;
	}

	public EncryptedStashesAndPaths(Map<Integer, EncryptedStash> encryptedStashes, Map<Integer, EncryptedBucket[]> paths,
									Map<Integer, Set<Integer>> versionPaths) {
		this.encryptedStashes = encryptedStashes;
		this.paths = paths;
		this.versionPaths = versionPaths;
	}

	public Map<Integer, EncryptedStash> getEncryptedStashes() {
		return encryptedStashes;
	}

	public Map<Integer, EncryptedBucket[]> getPaths() {
		return paths;
	}

	public Map<Integer, Set<Integer>> getVersionPaths() {
		return versionPaths;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(encryptedStashes.size());
		int[] keys = new int[encryptedStashes.size()];
		int i = 0;
		for (int key : encryptedStashes.keySet()) {
			keys[i++] = key;
		}
		Arrays.sort(keys);
		for (int key : keys) {
			out.writeInt(key);
			encryptedStashes.get(key).writeExternal(out);
		}
		out.writeInt(paths.size());
		keys = new int[paths.size()];
		i = 0;
		for (int key : paths.keySet()) {
			keys[i++] = key;
		}
		Arrays.sort(keys);
		for (int key : keys) {
			out.writeInt(key);
			EncryptedBucket[] encryptedBuckets = paths.get(key);
			out.writeInt(encryptedBuckets.length);
			for (EncryptedBucket encryptedBucket : encryptedBuckets) {
				encryptedBucket.writeExternal(out);
			}
		}

		out.writeInt(versionPaths.size());
		keys = new int[versionPaths.size()];
		i = 0;
		for (int key : versionPaths.keySet()) {
			keys[i++] = key;
		}
		Arrays.sort(keys);
		for (int key : keys) {
			out.writeInt(key);
			Set<Integer> versionIds = versionPaths.get(key);
			out.writeInt(versionIds.size());
			int[] orderedVersionIds = new int[versionIds.size()];
			int j = 0;
			for (int value : versionIds) {
				orderedVersionIds[j++] = value;
			}
			Arrays.sort(orderedVersionIds);
			for (int versionId : orderedVersionIds) {
				out.writeInt(versionId);
			}
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int size = in.readInt();
		ints += size;
		encryptedStashes = new HashMap<>(size);
		while (size-- > 0) {
			int versionId = in.readInt();
			EncryptedStash encryptedStash = new EncryptedStash();
			encryptedStash.readExternal(in);
			encryptedStashes.put(versionId, encryptedStash);
		}
		size = in.readInt();
		ints += size;
		paths = new HashMap<>(size);
		while (size-- > 0) {
			int versionId = in.readInt();
			int nValues = in.readInt();
			buckets += nValues;
			EncryptedBucket[] encryptedBuckets = new EncryptedBucket[nValues];
			for (int i = 0; i < nValues; i++) {
				EncryptedBucket bucket = new EncryptedBucket(oramContext.getBucketSize());
				bucket.readExternal(in);
				encryptedBuckets[i] = bucket;
			}
			paths.put(versionId, encryptedBuckets);
		}
		size = in.readInt();
		versionPaths = new HashMap<>(size);
		while (size-- > 0) {
			int outstandingId = in.readInt();
			int nValues = in.readInt();
			ints += (nValues+1);
			Set<Integer> versionIds = new HashSet<>(nValues);
			while (nValues-- > 0){
				versionIds.add(in.readInt());
			}
			versionPaths.put(outstandingId, versionIds);
		}
	}

	public int getSize(ORAMContext oramContext) {
		return ints * 8 + buckets * oramContext.getBucketSize() * oramContext.getBlockSize();
	}
}
