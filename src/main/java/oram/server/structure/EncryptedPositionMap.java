package oram.server.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class EncryptedPositionMap implements Externalizable {
	private byte[] encryptedPositionMap;

	public EncryptedPositionMap() {}

	public EncryptedPositionMap(byte[] encryptedPositionMap) {
		this.encryptedPositionMap = encryptedPositionMap;
	}

	public byte[] getEncryptedPositionMap() {
		return encryptedPositionMap;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(encryptedPositionMap == null ? -1 : encryptedPositionMap.length);
		if (encryptedPositionMap != null)
			out.write(encryptedPositionMap);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException {
		int len = in.readInt();
		if (len != -1) {
			encryptedPositionMap = new byte[len];
			in.readFully(encryptedPositionMap);
		}
	}
}
