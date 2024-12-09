package oram.client.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.HashMap;
import java.util.Map;

public class Stash implements RawCustomExternalizable {
	private final Map<Integer, Block> blocks;
	private final int blockSize;

	public Stash(int blockSize){
		this.blockSize = blockSize;
		this.blocks = new HashMap<>();
	}

	public void putBlock(Block block) {
		blocks.put(block.getAddress(), block);
	}

	public Map<Integer, Block> getBlocks() {
		return blocks;
	}

	public Block getBlock(int address){
		return blocks.get(address);
	}

	public Block getAndRemoveBlock(int address){
		return blocks.remove(address);
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;
		ORAMUtils.serializeInteger(blocks.size(), output, offset);
		offset += Integer.BYTES;

		for (Block block : blocks.values()) {
			offset = block.writeExternal(output, offset);
		}

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;
		int nBlocks = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		while (nBlocks-- > 0) {
			Block block = new Block(blockSize);
			offset = block.readExternal(input, offset);
			blocks.put(block.getAddress(), block);
		}

		return offset;
	}

	@Override
	public String toString() {
		if (blocks.size() > 60) {
			return blocks.size() + " blocks";
		}
		StringBuilder sb = new StringBuilder();
		blocks.keySet().stream().sorted().forEach(k -> {
			Block block = blocks.get(k);
			sb.append(block).append(" ");

		});
		return sb.toString();
	}

	public int getSerializedSize() {
		int size = Integer.BYTES;
		for (Block block : blocks.values()) {
			size += block.getSerializedSize();
		}
		return size;
	}
}
