package oram.client.structure;

import oram.utils.CustomExternalizable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Stash implements CustomExternalizable {
	private final List<Block> blocks;
	private final int blockSize;

	public Stash(int blockSize){
		this.blockSize = blockSize;
		this.blocks = new ArrayList<>();
	}

	public void putBlock(Block b) {
		blocks.add(b);
	}

	public List<Block> getBlocks() {
		return blocks;
	}

	public Block getBlock(int address){
		for (Block block : blocks) {
			if (block.getAddress() == address)
				return block;
		}
		return null;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(blocks.size());
		for (Block block : blocks){
			block.writeExternal(out);
		}
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		int size = in.readInt();
		while (size-- > 0) {
			Block block = new Block(blockSize);
			block.readExternal(in);
			blocks.add(block);
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Block block : blocks) {
			sb.append(block).append(", ");
		}
		return sb.toString();
	}
}
