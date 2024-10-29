package oram.server.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

public class EncryptedPositionMap implements RawCustomExternalizable {
	private byte[] encryptedPositionMap;

	public EncryptedPositionMap() {}

	public EncryptedPositionMap(byte[] encryptedPositionMap) {
		this.encryptedPositionMap = encryptedPositionMap;
	}

	public byte[] getEncryptedPositionMap() {
		return encryptedPositionMap;
	}

	public int getSerializedSize() {
		return 4 + (encryptedPositionMap == null ? 0 : encryptedPositionMap.length);
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;

		byte[] lenBytes = ORAMUtils.toBytes(encryptedPositionMap == null ? -1 : encryptedPositionMap.length);
		System.arraycopy(lenBytes, 0, output, offset, 4);
		offset += 4;

		if (encryptedPositionMap != null) {
			System.arraycopy(encryptedPositionMap, 0, output, offset, encryptedPositionMap.length);
			offset += encryptedPositionMap.length;
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
			encryptedPositionMap = new byte[len];
			System.arraycopy(input, offset, encryptedPositionMap, 0, len);
			offset += len;
		}
		return offset;
	}
}
