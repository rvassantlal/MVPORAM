package oram.messages;

import oram.server.structure.EncryptedPathMap;
import oram.server.structure.EncryptedStash;
import oram.utils.ORAMUtils;
import oram.utils.PositionMapType;

public class CreateORAMMessage extends ORAMMessage {
	private PositionMapType positionMapType;
	private int treeHeight;
	private int bucketSize;
	private int blockSize;
	private EncryptedPathMap encryptedPathMap;
	private EncryptedStash encryptedStash;

	public CreateORAMMessage() {}

	public CreateORAMMessage(int oramId, PositionMapType positionMapType,
							 int treeHeight, int bucketSize, int blockSize,
							 EncryptedPathMap encryptedPathMap, EncryptedStash encryptedStash) {
		super(oramId);
		this.positionMapType = positionMapType;
		this.treeHeight = treeHeight;
		this.bucketSize = bucketSize;
		this.blockSize = blockSize;
		this.encryptedPathMap = encryptedPathMap;
		this.encryptedStash = encryptedStash;
	}

	public PositionMapType getPositionMapType() {
		return positionMapType;
	}

	public int getTreeHeight() {
		return treeHeight;
	}

	public int getBucketSize() {
		return bucketSize;
	}

	public int getBlockSize() {
		return blockSize;
	}

	public EncryptedPathMap getEncryptedPathMap() {
		return encryptedPathMap;
	}

	public EncryptedStash getEncryptedStash() {
		return encryptedStash;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = super.writeExternal(output, startOffset);

		output[offset] = (byte) positionMapType.ordinal();
		offset++;

		ORAMUtils.serializeInteger(treeHeight, output, offset);
		offset += Integer.BYTES;

		ORAMUtils.serializeInteger(bucketSize, output, offset);
		offset += Integer.BYTES;

		ORAMUtils.serializeInteger(blockSize, output, offset);
		offset += Integer.BYTES;

		offset = encryptedPathMap.writeExternal(output, offset);
		offset = encryptedStash.writeExternal(output, offset);

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = super.readExternal(input, startOffset);

		positionMapType = PositionMapType.getPositionMapType(input[offset]);
		offset++;

		treeHeight = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		bucketSize = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		blockSize = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		encryptedPathMap = new EncryptedPathMap();
		offset = encryptedPathMap.readExternal(input, offset);

		encryptedStash = new EncryptedStash();
		offset = encryptedStash.readExternal(input, offset);

		return offset;
	}

	@Override
	public int getSerializedSize() {
		return super.getSerializedSize() + 1 + Integer.BYTES * 3 + encryptedPathMap.getSerializedSize()
				+ encryptedStash.getSerializedSize();
	}
}
