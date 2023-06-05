package oram.server.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class EncryptedStash implements Externalizable {
	private double versionId;
	private byte[] encryptedStash;

	public EncryptedStash() {}

	public EncryptedStash(double versionId, byte[] encryptedStash) {
		this.versionId = versionId;
		this.encryptedStash = encryptedStash;
	}

	public double getVersionId() {
		return versionId;
	}

	public byte[] getEncryptedStash() {
		return encryptedStash;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeDouble(versionId);
		out.writeInt(encryptedStash == null ? -1 : encryptedStash.length);
		if (encryptedStash != null) {
			out.write(encryptedStash);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		versionId = in.readDouble();
		int size = in.readInt();
		if (size != -1) {
			encryptedStash = new byte[size];
			in.readFully(encryptedStash);
		}
	}
}
