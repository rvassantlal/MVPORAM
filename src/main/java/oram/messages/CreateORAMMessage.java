package oram.messages;

import oram.server.structure.EncryptedPositionMap;
import oram.server.structure.EncryptedStash;
import oram.utils.ORAMUtils;
import oram.utils.PositionMapType;

public class CreateORAMMessage extends ORAMMessage {
	private PositionMapType positionMapType;
	private int treeHeight;
	private int nBlocksPerBucket;
	private int blockSize;
	private EncryptedPositionMap encryptedPositionMap;
	private EncryptedStash encryptedStash;

	public CreateORAMMessage() {}

	public CreateORAMMessage(int oramId, PositionMapType positionMapType,
							 int treeHeight, int nBlocksPerBucket, int blockSize,
							 EncryptedPositionMap encryptedPositionMap, EncryptedStash encryptedStash) {
		super(oramId);
		this.positionMapType = positionMapType;
		this.treeHeight = treeHeight;
		this.nBlocksPerBucket = nBlocksPerBucket;
		this.blockSize = blockSize;
		this.encryptedPositionMap = encryptedPositionMap;
		this.encryptedStash = encryptedStash;
	}

	public PositionMapType getPositionMapType() {
		return positionMapType;
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
	public int writeExternal(byte[] output, int startOffset) {
		int offset = super.writeExternal(output, startOffset);

		output[offset] = (byte) positionMapType.ordinal();
		offset++;

		byte[] treeHeightBytes = ORAMUtils.toBytes(treeHeight);
		System.arraycopy(treeHeightBytes, 0, output, offset, 4);
		offset += 4;

		byte[] nBlocksPerBucketBytes = ORAMUtils.toBytes(nBlocksPerBucket);
		System.arraycopy(nBlocksPerBucketBytes, 0, output, offset, 4);
		offset += 4;

		byte[] blockSizeBytes = ORAMUtils.toBytes(blockSize);
		System.arraycopy(blockSizeBytes, 0, output, offset, 4);
		offset += 4;

		offset = encryptedPositionMap.writeExternal(output, offset);
		offset = encryptedStash.writeExternal(output, offset);

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = super.readExternal(input, startOffset);

		positionMapType = PositionMapType.getPositionMapType(input[offset]);
		offset++;

		byte[] treeHeightBytes = new byte[4];
		System.arraycopy(input, offset, treeHeightBytes, 0, 4);
		offset += 4;
		treeHeight = ORAMUtils.toNumber(treeHeightBytes);

		byte[] nBlocksPerBucketBytes = new byte[4];
		System.arraycopy(input, offset, nBlocksPerBucketBytes, 0, 4);
		offset += 4;
		nBlocksPerBucket = ORAMUtils.toNumber(nBlocksPerBucketBytes);

		byte[] blockSizeBytes = new byte[4];
		System.arraycopy(input, offset, blockSizeBytes, 0, 4);
		offset += 4;
		blockSize = ORAMUtils.toNumber(blockSizeBytes);

		encryptedPositionMap = new EncryptedPositionMap();
		offset = encryptedPositionMap.readExternal(input, offset);

		encryptedStash = new EncryptedStash();
		offset = encryptedStash.readExternal(input, offset);

		return offset;
	}

	@Override
	public int getSerializedSize() {
		return super.getSerializedSize() + 1 + 4 * 3 + encryptedPositionMap.getSerializedSize()
				+ encryptedStash.getSerializedSize();
	}
}
