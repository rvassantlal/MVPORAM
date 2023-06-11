package oram.server.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

public class EncryptedBucket implements Externalizable {
	private final byte[][] blocks;

	public EncryptedBucket(int bucketSize) {
		this.blocks = new byte[bucketSize][];
	}
	public EncryptedBucket(byte[][] blocks) {
		this.blocks = blocks;
	}

	public byte[][] getBlocks() {
		return blocks;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		for (byte[] block : blocks) {
			out.writeInt(block.length);
			out.write(block);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
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
