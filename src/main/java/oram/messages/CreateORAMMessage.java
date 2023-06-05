package oram.messages;

import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class CreateORAMMessage extends ORAMMessage {
	private int treeHeight;
	private int nBlocksPerBucket;
	private int blockSize;
	private EncryptedPositionMap encryptedPositionMap;
	private EncryptedStash encryptedStash;

	public CreateORAMMessage() {}

	public CreateORAMMessage(int oramId, int treeHeight, int nBlocksPerBucket, int blockSize,
							 EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash) {
		super(oramId);
		this.treeHeight = treeHeight;
		this.nBlocksPerBucket = nBlocksPerBucket;
		this.blockSize = blockSize;
		this.encryptedPositionMap = encryptedPositionMap;
		this.encryptedStash = encryptedStash;
	}

	public int getTreeHeight() {
		return treeHeight;
	}

	public int getNBlocksPerBucket() {
		return nBlocksPerBucket;
	}

	public int getBlockSize() {
		return blockSize;
	}

	public EncryptedPositionMap getEncryptedPositionMap() {
		return encryptedPositionMap;
	}

	public EncryptedStash getEncryptedStash() {
		return encryptedStash;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeInt(treeHeight);
		out.writeInt(nBlocksPerBucket);
		out.writeInt(blockSize);
		encryptedPositionMap.writeExternal(out);
		encryptedStash.writeExternal(out);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		treeHeight = in.readInt();
		nBlocksPerBucket = in.readInt();
		blockSize = in.readInt();
		encryptedPositionMap = new EncryptedPositionMap();
		encryptedPositionMap.readExternal(in);
		encryptedStash = new EncryptedStash();
		encryptedStash.readExternal(in);
	}
}
