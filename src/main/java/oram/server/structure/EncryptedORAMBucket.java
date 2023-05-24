package oram.server.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class EncryptedORAMBucket implements Externalizable {
	private byte[][] blocks;
	private final int blockSize;

	public EncryptedORAMBucket(int bucketSize, int blockSize) {
		this.blocks = new byte[bucketSize][];
		this.blockSize = blockSize;
	}

	public byte[][] getBlocks() {
		return blocks;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		for (byte[] block : blocks) {
			out.write(block);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		for (int i = 0; i < blocks.length; i++) {
			blocks[i] = new byte[blockSize];
			in.readFully(blocks[i]);
		}
	}
}
