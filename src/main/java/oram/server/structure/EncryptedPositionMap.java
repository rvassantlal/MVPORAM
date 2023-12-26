package oram.server.structure;

import oram.utils.CustomExternalizable;

import java.io.*;

public class EncryptedPositionMap implements CustomExternalizable {
	private byte[] encryptedPositionMap;

	public EncryptedPositionMap() {}

	public EncryptedPositionMap(byte[] encryptedPositionMap) {
		this.encryptedPositionMap = encryptedPositionMap;
	}

	public byte[] getEncryptedPositionMap() {
		return encryptedPositionMap;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(encryptedPositionMap == null ? -1 : encryptedPositionMap.length);
		if (encryptedPositionMap != null)
			out.write(encryptedPositionMap);
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		int len = in.readInt();
		if (len != -1) {
			encryptedPositionMap = new byte[len];
			in.readFully(encryptedPositionMap);
		}
	}

	public int getSerializedSize() {
		return 4 + (encryptedPositionMap == null ? 0 : encryptedPositionMap.length);
	}
}
