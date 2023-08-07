package oram.server.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

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
	public void readExternal(ObjectInput in) throws IOException {
		int size = in.readInt();
		if (size != -1) {
			encryptedStash = new byte[size];
			in.readFully(encryptedStash);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		EncryptedStash that = (EncryptedStash) o;
		return Arrays.equals(encryptedStash, that.encryptedStash);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(encryptedStash);
	}
}
