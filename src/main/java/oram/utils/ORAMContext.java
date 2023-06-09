package oram.utils;

import java.util.Arrays;

public class ORAMContext {
	private final int TREE_HEIGHT; // number of levels is equal to tree height + 1
	private final int TREE_SIZE; // Number of buckets in the tree
	private final int BUCKET_SIZE;
	private final int BLOCK_SIZE;

	public ORAMContext(int treeHeight, int treeSize, int bucketSize, int blockSize) {
		this.TREE_HEIGHT = treeHeight;
		this.TREE_SIZE = treeSize;
		this.BUCKET_SIZE = bucketSize;
		this.BLOCK_SIZE = blockSize;
	}

	public int getTreeHeight() {
		return TREE_HEIGHT;
	}

	public int getTreeLevels() {
		return TREE_HEIGHT + 1;
	}

	public int getTreeSize() {
		return TREE_SIZE;
	}

	public int getBucketSize() {
		return BUCKET_SIZE;
	}

	public int getBlockSize() {
		return BLOCK_SIZE;
	}
}
