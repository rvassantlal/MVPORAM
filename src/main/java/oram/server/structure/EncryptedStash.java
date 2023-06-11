package oram.server.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class EncryptedStash implements Externalizable {
	private byte[] encryptedStash;

	public EncryptedStash() {}

	public EncryptedStash(byte[] encryptedStash) {
		this.encryptedStash = encryptedStash;
	}

	public byte[] getEncryptedStash() {
		return encryptedStash;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(encryptedStash == null ? -1 : encryptedStash.length);
		if (encryptedStash != null) {
			out.write(encryptedStash);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int size = in.readInt();
		if (size != -1) {
			encryptedStash = new byte[size];
			in.readFully(encryptedStash);
		}
	}
}
