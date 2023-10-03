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
	private Map<Integer, EncryptedStash> encryptedStashes;
	private Map<Integer, EncryptedBucket[]> paths;

	public EncryptedStashesAndPaths(ORAMContext oramContext) {
		this.oramContext = oramContext;
	}

	public EncryptedStashesAndPaths(Map<Integer, EncryptedStash> encryptedStashes,
									Map<Integer, EncryptedBucket[]> paths) {
		this.encryptedStashes = encryptedStashes;
		this.paths = paths;
	}

	public Map<Integer, EncryptedStash> getEncryptedStashes() {
		return encryptedStashes;
	}

	public Map<Integer, EncryptedBucket[]> getPaths() {
		return paths;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(encryptedStashes.size());
		for (Map.Entry<Integer, EncryptedStash> entry : encryptedStashes.entrySet()) {
			out.writeInt(entry.getKey());
			entry.getValue().writeExternal(out);
		}
		out.writeInt(paths.size());
		for (Map.Entry<Integer, EncryptedBucket[]> entry : paths.entrySet()) {
			out.writeInt(entry.getKey());
			out.writeInt(entry.getValue().length);
			for (EncryptedBucket encryptedBucket : entry.getValue()) {
				out.writeBoolean(encryptedBucket != null);
				if (encryptedBucket != null) {
					encryptedBucket.writeExternal(out);
				}
			}
		}
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		int size = in.readInt();
		encryptedStashes = new HashMap<>(size);
		while (size-- > 0) {
			int versionId = in.readInt();
			EncryptedStash encryptedStash = new EncryptedStash();
			encryptedStash.readExternal(in);
			encryptedStashes.put(versionId, encryptedStash);
		}
		size = in.readInt();
		paths = new HashMap<>(size);
		while (size-- > 0) {
			int versionId = in.readInt();
			int nValues = in.readInt();
			EncryptedBucket[] encryptedBuckets = new EncryptedBucket[nValues];
			for (int i = 0; i < nValues; i++) {
				if (!in.readBoolean()) {
					continue;
				}
				EncryptedBucket bucket = new EncryptedBucket(oramContext.getBucketSize());
				bucket.readExternal(in);
				encryptedBuckets[i] = bucket;
			}
			paths.put(versionId, encryptedBuckets);
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("EncryptedStashesAndPaths{");
		sb.append("\n\tencryptedStashes:\n\t\t").append(encryptedStashes);
		sb.append("\n\tpaths:");
		for (Map.Entry<Integer, EncryptedBucket[]> entry : paths.entrySet()) {
			sb.append("\n\t\t").append(entry.getKey()).append(" -> ").append(Arrays.toString(entry.getValue()));
		}
		sb.append("\n}");

		return sb.toString();
	}
}
