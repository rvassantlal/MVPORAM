package oram.server.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.Arrays;

public class EncryptedBucket implements RawCustomExternalizable {
	private final byte[][] blocks;
	private int location;

	public EncryptedBucket(int bucketSize) {
		this.blocks = new byte[bucketSize][];
	}

	public EncryptedBucket(byte[][] blocks, int location) {
		this.blocks = blocks;
		this.location = location;
	}

	public byte[][] getBlocks() {
		return blocks;
	}

	public int getLocation() {
		return location;
	}

	@Override
	public int getSerializedSize() {
		int size = 4;
		for (byte[] block : blocks) {
			size += 4 + block.length;
		}
		return size;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;

		ORAMUtils.serializeInteger(location, output, offset);
		offset += 4;

		for (byte[] block : blocks) {
			ORAMUtils.serializeInteger(block.length, output, offset);
			offset += 4;
			System.arraycopy(block, 0, output, offset, block.length);
			offset += block.length;
		}
		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;

		location = ORAMUtils.deserializeInteger(input, offset);
		offset += 4;

		for (int i = 0; i < blocks.length; i++) {
			int len = ORAMUtils.deserializeInteger(input, offset);
			offset += 4;

			blocks[i] = new byte[len];
			System.arraycopy(input, offset, blocks[i], 0, len);
			offset += len;
		}
		return offset;
	}

	@Override
	public String toString() {
		return String.valueOf(Arrays.deepHashCode(blocks));
	}
}
