package oram.server.structure;

import oram.utils.CustomExternalizable;
import oram.utils.ORAMContext;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class EncryptedStashesAndPaths implements CustomExternalizable {
	private ORAMContext oramContext;
	private int[] outstandingVersions;
	private EncryptedStash[] encryptedStashes;
	private EncryptedBucket[] paths;

	public EncryptedStashesAndPaths(ORAMContext oramContext) {
		this.oramContext = oramContext;
	}

	public EncryptedStashesAndPaths(int[] outstandingVersions, EncryptedStash[] encryptedStashes,
									EncryptedBucket[] paths) {
		this.outstandingVersions = outstandingVersions;
		this.encryptedStashes = encryptedStashes;
		this.paths = paths;
	}

	public int[] getOutstandingVersions() {
		return outstandingVersions;
	}

	public EncryptedStash[] getEncryptedStashes() {
		return encryptedStashes;
	}

	public EncryptedBucket[] getPaths() {
		return paths;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(outstandingVersions.length);
		for (int outstandingVersion : outstandingVersions) {
			out.writeInt(outstandingVersion);
		}
		for (EncryptedStash entry : encryptedStashes) {
			entry.writeExternal(out);
		}
		out.writeInt(paths.length);
		for (EncryptedBucket encryptedBucket : paths) {
			out.writeBoolean(encryptedBucket != null);
			if (encryptedBucket != null) {
				encryptedBucket.writeExternal(out);
			}
		}
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		int size = in.readInt();
		outstandingVersions = new int[size];
		for (int i = 0; i < size; i++) {
			outstandingVersions[i] = in.readInt();
		}
		encryptedStashes = new EncryptedStash[size];
		for (int i = 0; i < size; i++) {
			EncryptedStash encryptedStash = new EncryptedStash();
			encryptedStash.readExternal(in);
			encryptedStashes[i] = encryptedStash;

		}

		size = in.readInt();
		paths = new EncryptedBucket[size];
		for (int i = 0; i < size; i++) {
			if (!in.readBoolean()) {
				continue;
			}
			EncryptedBucket bucket = new EncryptedBucket(oramContext.getBucketSize());
			bucket.readExternal(in);
			paths[i] = bucket;

		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("EncryptedStashesAndPaths{");
		sb.append("\n\toutstandingVersions: ").append(Arrays.toString(outstandingVersions));
		sb.append("\n\tencryptedStashes: ").append(Arrays.deepHashCode(encryptedStashes));
		sb.append("\n\tpaths:");
		for (EncryptedBucket entry : paths) {
			sb.append("\n\t\t").append(entry.toString());
		}
		sb.append("\n}");

		return sb.toString();
	}
}
