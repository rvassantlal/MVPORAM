package oram.client.structure;

import java.util.Arrays;

public class Bucket {
	private final Block[] blocks;
	private final int blockSize;
	private final int location;

	public Bucket(int bucketSize, int blockSize, int location) {
		blocks = new Block[bucketSize];
		this.blockSize = blockSize;
		this.location = location;
	}

	public Block getBlock(int index) {
		return blocks[index];
	}

	public void putBlock(int index, Block block) {
		blocks[index] = block;
	}

	public int getBlockSize() {
		return blockSize;
	}

	public Block[] getBlocks() {
		return this.blocks;
	}

	public int getLocation() {
		return location;
	}

	public String toString() {
		return Arrays.toString(blocks);
	}
}
