package oram.server.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class ORAMContext implements Externalizable {
	private int treeHeight; // number of levels is equal to tree height + 1
	private int treeSize; // Number of buckets in the tree
	private int bucketSize;
	private int blockSize;

	public ORAMContext(int treeHeight, int treeSize, int bucketSize, int blockSize) {
		this.treeHeight = treeHeight;
		this.treeSize = treeSize;
		this.bucketSize = bucketSize;
		this.blockSize = blockSize;
	}

	public int getTreeHeight() {
		return treeHeight;
	}

	public int getTreeLevels() {
		return treeHeight + 1;
	}

	public int getTreeSize() {
		return treeSize;
	}

	public int getBucketSize() {
		return bucketSize;
	}

	public int getBlockSize() {
		return blockSize;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(treeHeight);
		out.writeInt(treeSize);
		out.writeInt(bucketSize);
		out.writeInt(blockSize);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		this.treeHeight = in.readInt();
		this.treeSize = in.readInt();
		this.bucketSize = in.readInt();
		this.blockSize = in.readInt();
	}
}
