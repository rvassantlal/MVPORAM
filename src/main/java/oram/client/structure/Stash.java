package oram.client.structure;

import oram.utils.CustomExternalizable;
import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Stash implements CustomExternalizable, RawCustomExternalizable {
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
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;
		byte[] nBlocksBytes = ORAMUtils.toBytes(blocks.size());
		System.arraycopy(nBlocksBytes, 0, output, offset, nBlocksBytes.length);
		offset += nBlocksBytes.length;

		for (Block block : blocks) {
			offset += block.writeExternal(output, offset);
		}

		return offset - startOffset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;
		byte[] nBlocksBytes = new byte[4];
		System.arraycopy(input, offset, nBlocksBytes, 0, nBlocksBytes.length);
		offset += nBlocksBytes.length;
		int nBlocks = ORAMUtils.toNumber(nBlocksBytes);

		for (int i = 0; i < nBlocks; i++) {
			Block block = new Block(blockSize);
			offset += block.readExternal(input, offset);
			blocks.add(block);
		}
		return offset - startOffset;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Block block : blocks) {
			sb.append(block).append(", ");
		}
		return sb.toString();
	}

	public int getSerializedSize() {
		int size = 4;
		for (Block block : blocks) {
			size += block.getSerializedSize();
		}
		return size;
	}
}
