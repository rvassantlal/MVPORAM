package oram.client.structure;

import oram.utils.CustomExternalizable;
import oram.utils.ORAMUtils;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;

public class Block implements CustomExternalizable {
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
		String contentString = content == null ? "null" : new String(content);
		return "Block{" + address + ", " + contentString + ", " + versionId + '}';
	}
}
