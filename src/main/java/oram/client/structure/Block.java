package oram.client.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.Arrays;
import java.util.Objects;

public class Block implements RawCustomExternalizable {
	private final int blockSize;
	private int address;
	private int contentVersion;
	private int locationVersion;
	private byte[] content;

	public Block(int blockSize) {
		this.blockSize = blockSize;
	}

	public Block(int blockSize, int address, int contentAndLocationVersion, byte[] newContent) {
		this.blockSize = blockSize;
		this.address = address;
		this.contentVersion = contentAndLocationVersion;
		this.content = newContent;
		this.locationVersion = contentAndLocationVersion;
	}

	public int getAddress() {
		return address;
	}

	public int getContentVersion() {
		return contentVersion;
	}

	public int getLocationVersion() {
		return locationVersion;
	}

	public void setContentVersion(int contentVersion) {
		this.contentVersion = contentVersion;
	}

	public void setLocationVersion(int locationVersion) {
		this.locationVersion = locationVersion;
	}

	public byte[] getContent() {
		return content;
	}

	public void setContent(byte[] newContent) {
		this.content = newContent;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;

		ORAMUtils.serializeInteger(address, output, offset);
		offset += 4;

		ORAMUtils.serializeInteger(contentVersion, output, offset);
		offset += 4;

		ORAMUtils.serializeInteger(locationVersion, output, offset);
		offset += 4;

		byte[] paddedContent = Arrays.copyOf(content, blockSize + 4);
		int emptyBytes = blockSize - content.length;
		byte[] serializedNEmptyBytes = ORAMUtils.toBytes(emptyBytes);
		System.arraycopy(serializedNEmptyBytes, 0, paddedContent, blockSize, 4);
		System.arraycopy(paddedContent, 0, output, offset, blockSize + 4);
		offset += blockSize + 4;

		return offset - startOffset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;

		address = ORAMUtils.deserializeInteger(input, offset);
		offset += 4;

		contentVersion = ORAMUtils.deserializeInteger(input, offset);
		offset += 4;

		locationVersion = ORAMUtils.deserializeInteger(input, offset);
		offset += 4;

		byte[] paddedContent = new byte[blockSize + 4];
		System.arraycopy(input, offset, paddedContent, 0, blockSize + 4);
		offset += blockSize + 4;
		byte[] serializedNEmptyBytes = new byte[4];
		System.arraycopy(paddedContent, blockSize, serializedNEmptyBytes, 0, 4);
		int emptyBytes = ORAMUtils.toNumber(serializedNEmptyBytes);
		content = Arrays.copyOf(paddedContent, blockSize - emptyBytes);

		return offset - startOffset;

	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Block)) return false;
		Block block = (Block) o;
		return address == block.address && contentVersion == block.contentVersion && locationVersion == block.locationVersion;
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, contentVersion, locationVersion);
	}

	@Override
	public String toString() {
		return "B(A: " + address + ", CV: " + contentVersion + ", LV: " + locationVersion + ")";
	}

	public int getSerializedSize() {
		return 4 * Integer.BYTES + blockSize;
	}
}
