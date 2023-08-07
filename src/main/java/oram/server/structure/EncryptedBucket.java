package oram.server.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.Objects;

public class EncryptedBucket implements Externalizable {
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
	public void writeExternal(ObjectOutput out) throws IOException {
		for (byte[] block : blocks) {
			out.writeInt(block.length);
			out.write(block);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException {
		for (int i = 0; i < blocks.length; i++) {
			blocks[i] = new byte[in.readInt()];
			in.readFully(blocks[i]);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		EncryptedBucket that = (EncryptedBucket) o;
		return tainted == that.tainted && Arrays.equals(blocks, that.blocks);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(tainted);
		result = 31 * result + Arrays.hashCode(blocks);
		return result;
	}

	@Override
	public String toString() {
		return "EncryptedBucket{" +
				Arrays.toString(blocks) +
				'}';
	}
}
