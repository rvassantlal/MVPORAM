package oram.utils;

public class ORAMContext {
	private final int TREE_HEIGHT; // number of levels is equal to tree height + 1
	private final int TREE_SIZE; // Number of buckets and blocks in the tree
	private final int BUCKET_SIZE;
	private final int BLOCK_SIZE;
	private final int K;

	public ORAMContext(int treeHeight, int bucketSize, int blockSize) {
		this.TREE_HEIGHT = treeHeight;
		this.TREE_SIZE = ORAMUtils.computeNumberOfNodes(treeHeight);
		this.BUCKET_SIZE = bucketSize;
		this.BLOCK_SIZE = blockSize;
		this.K = bucketSize;
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

	public int getK() {
		return K;
	}
}
