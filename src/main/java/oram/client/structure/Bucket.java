package oram.client.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

public class Bucket implements Externalizable {
	private final Block[] blocks;
	private final int blockSize;
	private int index;

	public Bucket(int bucketSize, int blockSize){
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
	public void writeExternal(ObjectOutput objectOutput) throws IOException {
		for (Block block : blocks){
			block.writeExternal(objectOutput);
		}
	}

	@Override
	public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
		for (int i = 0; i < blocks.length; i++) {
			Block block = new Block(blockSize);
			block.readExternal(objectInput);
		}
	}
}
