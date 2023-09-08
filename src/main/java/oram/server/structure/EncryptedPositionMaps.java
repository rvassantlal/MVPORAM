package oram.server.structure;

import vss.secretsharing.VerifiableShare;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

public class EncryptedPositionMaps implements Externalizable {

	private int newVersionId;
	private int[] outstandingVersionIds;
	private EncryptedPositionMap[] encryptedPositionMaps;
	private VerifiableShare[] encryptionKeyShares;

	public EncryptedPositionMaps(){}

	public EncryptedPositionMaps(int newVersionId, int[] outstandingVersionIds,
								 EncryptedPositionMap[] encryptedPositionMaps, VerifiableShare[] encryptionKeyShares) {
		this.newVersionId = newVersionId;
		this.outstandingVersionIds = outstandingVersionIds;
		this.encryptedPositionMaps = encryptedPositionMaps;
		this.encryptionKeyShares = encryptionKeyShares;
	}

	public VerifiableShare[] getEncryptionKeyShares() {
		return encryptionKeyShares;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(newVersionId);
		out.writeInt(outstandingVersionIds.length);
		for (int outstandingVersionId : outstandingVersionIds) {
			out.writeInt(outstandingVersionId);
		}
		out.writeInt(encryptedPositionMaps.length);
		for (EncryptedPositionMap encryptedPositionMap : encryptedPositionMaps) {
			encryptedPositionMap.writeExternal(out);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		newVersionId = in.readInt();
		int size = in.readInt();
		outstandingVersionIds = new int[size];
		for (int i = 0; i < size; i++) {
			outstandingVersionIds[i] = in.readInt();
		}
		size = in.readInt();
		encryptedPositionMaps = new EncryptedPositionMap[size];
		for (int i = 0; i < size; i++) {
			EncryptedPositionMap e = new EncryptedPositionMap();
			e.readExternal(in);
			encryptedPositionMaps[i] = e;
		}
	}

	public int[] getOutstandingVersionIds() {
		return outstandingVersionIds;
	}

	public EncryptedPositionMap[] getEncryptedPositionMaps() {
		return encryptedPositionMaps;
	}

	public int getNewVersionId() {
		return newVersionId;
	}

	@Override
	public String toString() {
		return "EncryptedPositionMaps{" +
				"newVersionId=" + newVersionId +
				", outstandingVersionIds=" + Arrays.hashCode(outstandingVersionIds) +
				", encryptedPositionMaps=" + Arrays.toString(encryptedPositionMaps) +
				'}';
	}
}
