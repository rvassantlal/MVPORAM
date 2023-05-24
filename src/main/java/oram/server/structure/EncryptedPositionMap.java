package oram.server.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class EncryptedPositionMap implements Externalizable {
	private double versionId;
	private byte[] encryptedPositionMap;

	public EncryptedPositionMap() {}

	public EncryptedPositionMap(double versionId, byte[] encryptedPositionMap) {
		this.versionId = versionId;
		this.encryptedPositionMap = encryptedPositionMap;
	}

	public double getVersionId() {
		return versionId;
	}

	public byte[] getEncryptedPositionMap() {
		return encryptedPositionMap;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeDouble(versionId);
		out.writeInt(encryptedPositionMap == null ? -1 : encryptedPositionMap.length);//TODO this length is fixed
		if (encryptedPositionMap != null)
			out.write(encryptedPositionMap);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		versionId = in.readDouble();
		int len = in.readInt();
		if (len != -1) {
			encryptedPositionMap = new byte[len];
			in.readFully(encryptedPositionMap);
		}
	}
}
