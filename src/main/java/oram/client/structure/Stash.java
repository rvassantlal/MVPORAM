package oram.client.structure;

import oram.utils.CustomExternalizable;
import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Stash implements CustomExternalizable, RawCustomExternalizable {
	private final List<Block> blocks;
	private final int blockSize;
	private final Map<Integer, Integer> evictedBlocks;

	public Stash(int blockSize){
		this.blockSize = blockSize;
		this.blocks = new ArrayList<>();
		this.evictedBlocks = new HashMap<>();
	}

	public void putBlock(Block b) {
		blocks.add(b);
	}

	public void addEvictedBlock(int address, int version) {
		evictedBlocks.put(address, version);
	}

	public Map<Integer, Integer> getEvictedBlocks() {
		return evictedBlocks;
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
		out.writeInt(evictedBlocks.size());
		int[] orderedKeys = evictedBlocks.keySet().stream().mapToInt(i -> i).sorted().toArray();
		for (int key : orderedKeys) {
			out.writeInt(key);
			out.writeInt(evictedBlocks.get(key));
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

		int evictedSize = in.readInt();
		while (evictedSize-- > 0) {
			int key = in.readInt();
			int value = in.readInt();
			evictedBlocks.put(key, value);
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

		byte[] evictedSizeBytes = ORAMUtils.toBytes(evictedBlocks.size());
		System.arraycopy(evictedSizeBytes, 0, output, offset, evictedSizeBytes.length);
		offset += evictedSizeBytes.length;
		int[] orderedKeys = evictedBlocks.keySet().stream().mapToInt(i -> i).sorted().toArray();
		for (int key : orderedKeys) {
			byte[] keyBytes = ORAMUtils.toBytes(key);
			System.arraycopy(keyBytes, 0, output, offset, keyBytes.length);
			offset += keyBytes.length;
			byte[] valueBytes = ORAMUtils.toBytes(evictedBlocks.get(key));
			System.arraycopy(valueBytes, 0, output, offset, valueBytes.length);
			offset += valueBytes.length;
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

		byte[] evictedSizeBytes = new byte[4];
		System.arraycopy(input, offset, evictedSizeBytes, 0, evictedSizeBytes.length);
		offset += evictedSizeBytes.length;
		int evictedSize = ORAMUtils.toNumber(evictedSizeBytes);
		while (evictedSize-- > 0) {
			byte[] keyBytes = new byte[4];
			System.arraycopy(input, offset, keyBytes, 0, keyBytes.length);
			offset += keyBytes.length;
			int key = ORAMUtils.toNumber(keyBytes);
			byte[] valueBytes = new byte[4];
			System.arraycopy(input, offset, valueBytes, 0, valueBytes.length);
			offset += valueBytes.length;
			int value = ORAMUtils.toNumber(valueBytes);
			evictedBlocks.put(key, value);
		}

		return offset - startOffset;
	}

	@Override
	public String toString() {
		if (blocks.size() > 30) {
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
		size += 4 + 8 * evictedBlocks.size();
		return size;
	}
}
