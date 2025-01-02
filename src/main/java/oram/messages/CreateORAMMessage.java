package oram.messages;

import oram.server.structure.EncryptedPathMap;
import oram.server.structure.EncryptedStash;
import oram.utils.ORAMUtils;

public class CreateORAMMessage extends ORAMMessage {
	private int treeHeight;
	private int bucketSize;
	private int blockSize;
	private EncryptedPathMap encryptedPathMap;
	private EncryptedStash encryptedStash;

	public CreateORAMMessage() {}

	public CreateORAMMessage(int oramId, int treeHeight, int bucketSize, int blockSize,
							 EncryptedPathMap encryptedPathMap, EncryptedStash encryptedStash) {
		super(oramId);
		this.treeHeight = treeHeight;
		this.bucketSize = bucketSize;
		this.blockSize = blockSize;
		this.encryptedPathMap = encryptedPathMap;
		this.encryptedStash = encryptedStash;
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
		return super.getSerializedSize() + Integer.BYTES * 3 + encryptedPathMap.getSerializedSize()
				+ encryptedStash.getSerializedSize();
	}
}
