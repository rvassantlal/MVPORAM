package oram.client.structure;

import oram.utils.CustomExternalizable;
import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;

public class Block implements CustomExternalizable, RawCustomExternalizable {
	private final int blockSize;
	private int address;
	private int versionId;
	private byte[] content;

	public Block(int blockSize) {
		this.blockSize = blockSize;
	}

	public Block(int blockSize, int address, int versionId, byte[] newContent) {
		this.blockSize = blockSize;
		this.address = address;
		this.versionId = versionId;
		this.content = newContent;
	}

	public int getAddress() {
		return address;
	}

	public int getVersionId() {
		return versionId;
	}

	public void setVersionId(int versionId) {
		this.versionId = versionId;
	}

	public byte[] getContent() {
		return content;
	}

	public void setContent(byte[] newContent) {
		this.content = newContent;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(address);
		out.writeInt(versionId);
		byte[] paddedContent = Arrays.copyOf(content, blockSize + 4);
		int emptyBytes = blockSize - content.length;
		byte[] serializedNEmptyBytes = ORAMUtils.toBytes(emptyBytes);
		System.arraycopy(serializedNEmptyBytes, 0, paddedContent, blockSize, 4);
		out.write(paddedContent);
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		address = in.readInt();
		versionId = in.readInt();
		byte[] paddedContent = new byte[blockSize + 4];
		in.readFully(paddedContent);
		byte[] serializedNEmptyBytes = new byte[4];
		System.arraycopy(paddedContent, blockSize, serializedNEmptyBytes, 0, 4);
		int emptyBytes = ORAMUtils.toNumber(serializedNEmptyBytes);
		content = Arrays.copyOf(paddedContent, blockSize - emptyBytes);
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;
		byte[] addressBytes = ORAMUtils.toBytes(address);
		System.arraycopy(addressBytes, 0, output, offset, 4);
		offset += 4;

		byte[] versionIdBytes = ORAMUtils.toBytes(versionId);
		System.arraycopy(versionIdBytes, 0, output, offset, 4);
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
		byte[] addressBytes = new byte[4];
		System.arraycopy(input, offset, addressBytes, 0, 4);
		offset += 4;
		address = ORAMUtils.toNumber(addressBytes);

		byte[] versionIdBytes = new byte[4];
		System.arraycopy(input, offset, versionIdBytes, 0, 4);
		offset += 4;
		versionId = ORAMUtils.toNumber(versionIdBytes);

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
		if (o == null || getClass() != o.getClass()) return false;
		Block block = (Block) o;
		return address == block.address && Arrays.equals(content, block.content);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(address);
		result = 31 * result + Arrays.hashCode(content);
		return result;
	}

	@Override
	public String toString() {
		//String contentString = content == null ? "null" : new String(content);
		//return "Block{" + address + ", " + contentString + ", " + versionId + '}';
		return "B(" + address + ", " + versionId + ')';
	}

	public int getSerializedSize() {
		return 4 * 3 + blockSize;
	}
}
