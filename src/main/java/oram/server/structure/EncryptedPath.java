package oram.server.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class EncryptedPath implements Externalizable {
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
	public void writeExternal(ObjectOutput out) throws IOException {
		for (EncryptedBucket encryptedBucket : encryptedBuckets) {
			encryptedBucket.writeExternal(out);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		for (int i = 0; i < encryptedBuckets.length; i++) {
			EncryptedBucket encryptedBucket = new EncryptedBucket(bucketSize);
			encryptedBucket.readExternal(in);
			encryptedBuckets[i] = encryptedBucket;
		}
	}
}
