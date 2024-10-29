package oram.client.structure;

import java.util.Arrays;

public class Bucket {
	private final Block[] blocks;
	private final int blockSize;
	private final int location;
	private int index;

	public Bucket(int bucketSize, int blockSize, int location) {
		blocks = new Block[bucketSize];
		this.blockSize = blockSize;
		this.location = location;
	}

	public boolean putBlock(Block block) {
		if (index >= blocks.length)
			return false;
		blocks[index++] = block;
		return true;
	}

	public int getBlockSize() {
		return blockSize;
	}

	public Block[] readBucket() {
		return this.blocks;
	}

	public int getLocation() {
		return location;
	}

	public String toString() {
		return Arrays.toString(blocks);
	}
}
