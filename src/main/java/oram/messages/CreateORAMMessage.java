package oram.messages;

import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;
import oram.utils.PositionMapType;

import java.io.*;

public class CreateORAMMessage extends ORAMMessage {
	private PositionMapType positionMapType;
	private int garbageCollectionFrequency;
	private int treeHeight;
	private int nBlocksPerBucket;
	private int blockSize;
	private EncryptedPositionMap encryptedPositionMap;
	private EncryptedStash encryptedStash;

	public CreateORAMMessage() {}

	public CreateORAMMessage(int oramId, PositionMapType positionMapType, int garbageCollectionFrequency,
							 int treeHeight, int nBlocksPerBucket, int blockSize,
							 EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash) {
		super(oramId);
		this.positionMapType = positionMapType;
		this.garbageCollectionFrequency = garbageCollectionFrequency;
		this.treeHeight = treeHeight;
		this.nBlocksPerBucket = nBlocksPerBucket;
		this.blockSize = blockSize;
		this.encryptedPositionMap = encryptedPositionMap;
		this.encryptedStash = encryptedStash;
	}

	public PositionMapType getPositionMapType() {
		return positionMapType;
	}

	public int getGarbageCollectionFrequency() {
		return garbageCollectionFrequency;
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
	public void writeExternal(DataOutput out) throws IOException {
		super.writeExternal(out);
		out.writeByte(positionMapType.ordinal());
		out.writeInt(garbageCollectionFrequency);
		out.writeInt(treeHeight);
		out.writeInt(nBlocksPerBucket);
		out.writeInt(blockSize);
		encryptedPositionMap.writeExternal(out);
		encryptedStash.writeExternal(out);
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		super.readExternal(in);
		positionMapType = PositionMapType.getPositionMapType(in.readByte());
		garbageCollectionFrequency = in.readInt();
		treeHeight = in.readInt();
		nBlocksPerBucket = in.readInt();
		blockSize = in.readInt();
		encryptedPositionMap = new EncryptedPositionMap();
		encryptedPositionMap.readExternal(in);
		encryptedStash = new EncryptedStash();
		encryptedStash.readExternal(in);
	}
}
