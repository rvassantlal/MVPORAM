package oram.client.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Stash implements RawCustomExternalizable {
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
		if (blocks.size() > 60) {
			return blocks.size() + " blocks";
		}
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
