package oram.client.structure;

import oram.utils.CustomExternalizable;

import java.io.*;
import java.util.Arrays;

public class Bucket implements CustomExternalizable {
	private final Block[] blocks;
	private final int blockSize;
	private int index;

	public Bucket(int bucketSize, int blockSize) {
		blocks = new Block[bucketSize];
		this.blockSize = blockSize;
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

	public String toString() {
		return Arrays.toString(blocks);
	}


	@Override
	public void writeExternal(DataOutput out) throws IOException {
		for (Block block : blocks) {
			block.writeExternal(out);
		}
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		for (int i = 0; i < blocks.length; i++) {
			Block block = new Block(blockSize);
			block.readExternal(in);
		}
	}
}
