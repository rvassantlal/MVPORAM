package oram.server.structure;

import oram.utils.CustomExternalizable;
import oram.utils.ORAMContext;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class EncryptedStashesAndPaths implements CustomExternalizable {
	private ORAMContext oramContext;
	private EncryptedStash[] encryptedStashes;
	private EncryptedBucket[] paths;

	public EncryptedStashesAndPaths(ORAMContext oramContext) {
		this.oramContext = oramContext;
	}

	public EncryptedStashesAndPaths(EncryptedStash[] encryptedStashes, EncryptedBucket[] paths) {
		this.encryptedStashes = encryptedStashes;
		this.paths = paths;
	}

	public EncryptedStash[] getEncryptedStashes() {
		return encryptedStashes;
	}

	public EncryptedBucket[] getPaths() {
		return paths;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(encryptedStashes.length);
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

	public int getSerializedSize() {
		int size = 4;
		for (EncryptedStash entry : encryptedStashes) {
			size += entry.getSerializedSize();
		}
		size += 4;
		for (EncryptedBucket encryptedBucket : paths) {
			size += 1;
			if (encryptedBucket != null) {
				size += encryptedBucket.getSerializedSize();
			}
		}
		return size;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("EncryptedStashesAndPaths{");
		sb.append("\n\tencryptedStashes: ").append(Arrays.deepHashCode(encryptedStashes));
		sb.append("\n\tpaths:");
		for (EncryptedBucket entry : paths) {
			sb.append("\n\t\t").append(entry.toString());
		}
		sb.append("\n}");

		return sb.toString();
	}
}
