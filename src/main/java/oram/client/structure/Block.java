package oram.client.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.Arrays;
import java.util.Objects;

public class Block implements RawCustomExternalizable {
	private final int blockSize;
	private int address;
	private int version;
	private int access;
	private byte[] content;

	public Block(int blockSize) {
		this.blockSize = blockSize;
	}

	public Block(int blockSize, int address, int writeAndAccessVersion, byte[] newContent) {
		this.blockSize = blockSize;
		this.address = address;
		this.version = writeAndAccessVersion;
		this.content = newContent;
		this.access = writeAndAccessVersion;
	}

	public int getAddress() {
		return address;
	}

	public int getVersion() {
		return version;
	}

	public int getAccess() {
		return access;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public void setAccess(int access) {
		this.access = access;
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

		ORAMUtils.serializeInteger(version, output, offset);
		offset += 4;

		ORAMUtils.serializeInteger(access, output, offset);
		offset += 4;

		byte[] paddedContent = Arrays.copyOf(content, blockSize + 4);
		int emptyBytes = blockSize - content.length;
		byte[] serializedNEmptyBytes = ORAMUtils.toBytes(emptyBytes);
		System.arraycopy(serializedNEmptyBytes, 0, paddedContent, blockSize, 4);
		System.arraycopy(paddedContent, 0, output, offset, blockSize + 4);
		offset += blockSize + 4;

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;

		address = ORAMUtils.deserializeInteger(input, offset);
		offset += 4;

		version = ORAMUtils.deserializeInteger(input, offset);
		offset += 4;

		access = ORAMUtils.deserializeInteger(input, offset);
		offset += 4;

		byte[] paddedContent = new byte[blockSize + 4];
		System.arraycopy(input, offset, paddedContent, 0, blockSize + 4);
		offset += blockSize + 4;
		byte[] serializedNEmptyBytes = new byte[4];
		System.arraycopy(paddedContent, blockSize, serializedNEmptyBytes, 0, 4);
		int emptyBytes = ORAMUtils.toNumber(serializedNEmptyBytes);
		content = Arrays.copyOf(paddedContent, blockSize - emptyBytes);

		return offset;

	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Block)) return false;
		Block block = (Block) o;
		return address == block.address && version == block.version && access == block.access;
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, version, access);
	}

	@Override
	public String toString() {
		return "B(ADDR: " + address + ", V: " + version + ", A: " + access + ")";
	}

	public int getSerializedSize() {
		return 4 * Integer.BYTES + blockSize;
	}
}
