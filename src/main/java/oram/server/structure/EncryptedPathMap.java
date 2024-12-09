package oram.server.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

public class EncryptedPathMap implements RawCustomExternalizable {
	private byte[] encryptedPathMap;

	public EncryptedPathMap() {}

	public EncryptedPathMap(byte[] encryptedPathMap) {
		this.encryptedPathMap = encryptedPathMap;
	}

	public byte[] getEncryptedPathMap() {
		return encryptedPathMap;
	}

	public int getSerializedSize() {
		return 4 + (encryptedPathMap == null ? 0 : encryptedPathMap.length);
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;

		ORAMUtils.serializeInteger(encryptedPathMap == null ? -1 : encryptedPathMap.length, output, offset);
		offset += Integer.BYTES;

		if (encryptedPathMap != null) {
			System.arraycopy(encryptedPathMap, 0, output, offset, encryptedPathMap.length);
			offset += encryptedPathMap.length;
		}

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;

		int len = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		if (len != -1) {
			encryptedPathMap = new byte[len];
			System.arraycopy(input, offset, encryptedPathMap, 0, len);
			offset += len;
		}
		return offset;
	}
}
