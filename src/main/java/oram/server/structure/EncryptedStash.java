package oram.server.structure;

import oram.utils.CustomExternalizable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class EncryptedStash implements CustomExternalizable {
	private byte[] encryptedStash;

	public EncryptedStash() {}

	public EncryptedStash(byte[] encryptedStash) {
		this.encryptedStash = encryptedStash;
	}

	public byte[] getEncryptedStash() {
		return encryptedStash;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(encryptedStash == null ? -1 : encryptedStash.length);
		if (encryptedStash != null) {
			out.write(encryptedStash);
		}
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		int size = in.readInt();
		if (size != -1) {
			encryptedStash = new byte[size];
			in.readFully(encryptedStash);
		}
	}

	@Override
	public String toString() {
		return String.valueOf(Arrays.hashCode(encryptedStash));
	}
}
