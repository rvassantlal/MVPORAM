package oram.server.structure;

import oram.utils.CustomExternalizable;

import java.io.*;

public class EncryptedPath implements CustomExternalizable {
	private final EncryptedBucket[] encryptedBuckets;
	private final int bucketSize;
	private final int blockSize;

	public EncryptedPath(int treeHeight, int bucketSize, int blockSize) {
		this.encryptedBuckets = new EncryptedBucket[treeHeight + 1];
		this.bucketSize = bucketSize;
		this.blockSize = blockSize;
	}

	public EncryptedPath(EncryptedBucket[] encryptedBuckets, int bucketSize, int blockSize) {
		this.encryptedBuckets = encryptedBuckets;
		this.bucketSize = bucketSize;
		this.blockSize = blockSize;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		for (EncryptedBucket encryptedBucket : encryptedBuckets) {
			encryptedBucket.writeExternal(out);
		}
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		for (int i = 0; i < encryptedBuckets.length; i++) {
			EncryptedBucket encryptedBucket = new EncryptedBucket(bucketSize);
			encryptedBucket.readExternal(in);
			encryptedBuckets[i] = encryptedBucket;
		}
	}
}
