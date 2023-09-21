package oram.server.structure;

import oram.utils.CustomExternalizable;

import java.io.*;
import java.util.Arrays;

public class EncryptedBucket implements CustomExternalizable {
	private final byte[][] blocks;

	private boolean tainted;

	public EncryptedBucket(int bucketSize) {
		this.blocks = new byte[bucketSize][];
	}

	public EncryptedBucket(byte[][] blocks) {
		this.blocks = blocks;
	}

	public byte[][] getBlocks() {
		return blocks;
	}

	public void taintBucket() {
		tainted = true;
	}

	public void untaintBucket() {
		tainted = false;
	}

	public boolean isTainted() {
		return tainted;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		for (byte[] block : blocks) {
			out.writeInt(block.length);
			out.write(block);
		}
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		for (int i = 0; i < blocks.length; i++) {
			blocks[i] = new byte[in.readInt()];
			in.readFully(blocks[i]);
		}
	}

	@Override
	public String toString() {
		return "EncryptedBucket{" +
				Arrays.toString(blocks) +
				'}';
	}
}
