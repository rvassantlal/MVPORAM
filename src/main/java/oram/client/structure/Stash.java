package oram.client.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.LinkedList;
import java.util.List;

public class Stash implements Externalizable {
	private final List<Block> blocks;
	private final int blockSize;
	private double versionId;

	public Stash(int blockSize){
		this.blockSize = blockSize;
		this.blocks = new LinkedList<>();
	}

	public Stash(int blockSize, double versionId ){
		this.blockSize = blockSize;
		this.versionId = versionId;
		this.blocks = new LinkedList<>();
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

	public void remove(Block block) {
		blocks.remove(block);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(blocks.size());
		for (Block block : blocks){
			block.writeExternal(out);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int size = blocks.size();
		while (size-- > 0) {
			Block block = new Block(blockSize);
			block.readExternal(in);
		}
	}

	public double getVersionId() {
		return versionId;
	}
}
