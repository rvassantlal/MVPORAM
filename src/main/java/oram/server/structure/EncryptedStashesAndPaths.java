package oram.server.structure;

import oram.utils.CustomExternalizable;
import oram.utils.ORAMContext;

import java.io.*;
import java.util.*;

public class EncryptedStashesAndPaths implements CustomExternalizable {
	private ORAMContext oramContext;
	private Map<Integer, EncryptedStash> encryptedStashes;
	private Map<Integer, EncryptedBucket[]> paths;
	private int ints = 0;
	private int buckets = 0;

	public EncryptedStashesAndPaths() {
	}

	public EncryptedStashesAndPaths(ORAMContext oramContext) {
		this.oramContext = oramContext;
	}

	public EncryptedStashesAndPaths(Map<Integer, EncryptedStash> encryptedStashes,
									Map<Integer, EncryptedBucket[]> paths) {
		this.encryptedStashes = encryptedStashes;
		this.paths = paths;
	}

	public Map<Integer, EncryptedStash> getEncryptedStashes() {
		return encryptedStashes;
	}

	public Map<Integer, EncryptedBucket[]> getPaths() {
		return paths;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
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
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
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
	}

	public int getSize(ORAMContext oramContext) {
		return ints * 8 + buckets * oramContext.getBucketSize() * oramContext.getBlockSize();
	}
}
