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
		for (Map.Entry<Integer, EncryptedStash> entry : encryptedStashes.entrySet()) {
			out.writeInt(entry.getKey());
			entry.getValue().writeExternal(out);
		}
		out.writeInt(paths.size());
		for (Map.Entry<Integer, EncryptedBucket[]> entry : paths.entrySet()) {
			out.writeInt(entry.getKey());
			out.writeInt(entry.getValue().length);
			for (EncryptedBucket encryptedBucket : entry.getValue()) {
				encryptedBucket.writeExternal(out);
			}
		}
		out.writeInt(versionPaths.size());
		for (Map.Entry<Integer, Set<Integer>> entry : versionPaths.entrySet()) {
			out.writeInt(entry.getKey());
			Set<Integer> versionIds = entry.getValue();
			out.writeInt(versionIds.size());
			for (int versionId : versionIds) {
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
