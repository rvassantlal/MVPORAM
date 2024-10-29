package oram.server.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.Arrays;

public class EncryptedStash implements RawCustomExternalizable {
	private byte[] encryptedStash;

	public EncryptedStash() {}

	public EncryptedStash(byte[] encryptedStash) {
		this.encryptedStash = encryptedStash;
	}

	public byte[] getEncryptedStash() {
		return encryptedStash;
	}

	public int getSerializedSize() {
		return 4 + (encryptedStash == null ? 0 : encryptedStash.length);
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;

		byte[] lenBytes = ORAMUtils.toBytes(encryptedStash == null ? -1 : encryptedStash.length);
		System.arraycopy(lenBytes, 0, output, offset, 4);
		offset += 4;

		if (encryptedStash != null) {
			System.arraycopy(encryptedStash, 0, output, offset, encryptedStash.length);
			offset += encryptedStash.length;
		}

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;
		byte[] lenBytes = new byte[4];
		System.arraycopy(input, offset, lenBytes, 0, 4);
		offset += 4;
		int len = ORAMUtils.toNumber(lenBytes);
		if (len != -1) {
			encryptedStash = new byte[len];
			System.arraycopy(input, offset, encryptedStash, 0, len);
			offset += len;
		}
		return offset;
	}

	@Override
	public String toString() {
		return Arrays.toString(encryptedStash);
	}
}
