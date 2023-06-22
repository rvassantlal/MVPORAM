package oram.server.structure;

import oram.utils.ORAMContext;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;

public class EncryptedStashesAndPaths implements Externalizable {
	private ORAMContext oramContext;
	private Map<Double, EncryptedStash> encryptedStashes;
	private Map<Double, EncryptedBucket[]> paths;
	private Map<Double, Set<Double>> versionPaths;

	public EncryptedStashesAndPaths() {
	}

	public EncryptedStashesAndPaths(ORAMContext oramContext) {
		this.oramContext = oramContext;
	}

	public EncryptedStashesAndPaths(Map<Double, EncryptedStash> encryptedStashes, Map<Double, EncryptedBucket[]> paths,
									Map<Double, Set<Double>> versionPaths) {
		this.encryptedStashes = encryptedStashes;
		this.paths = paths;
		this.versionPaths = versionPaths;
	}

	public Map<Double, EncryptedStash> getEncryptedStashes() {
		return encryptedStashes;
	}

	public Map<Double, EncryptedBucket[]> getPaths() {
		return paths;
	}

	public Map<Double, Set<Double>> getVersionPaths() {
		return versionPaths;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(encryptedStashes.size());
		for (Map.Entry<Double, EncryptedStash> entry : encryptedStashes.entrySet()) {
			out.writeDouble(entry.getKey());
			entry.getValue().writeExternal(out);
		}
		out.writeInt(paths.size());
		for (Map.Entry<Double, EncryptedBucket[]> entry : paths.entrySet()) {
			out.writeDouble(entry.getKey());
			out.writeInt(entry.getValue().length);
			for (EncryptedBucket encryptedBucket : entry.getValue()) {
				encryptedBucket.writeExternal(out);
			}
		}
		out.writeInt(versionPaths.size());
		for (Map.Entry<Double, Set<Double>> entry : versionPaths.entrySet()) {
			out.writeDouble(entry.getKey());
			Set<Double> versionIds = entry.getValue();
			out.writeInt(versionIds.size());
			for (double versionId : versionIds) {
				out.writeDouble(versionId);
			}
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int size = in.readInt();
		encryptedStashes = new HashMap<>(size);
		while (size-- > 0) {
			double versionId = in.readDouble();
			EncryptedStash encryptedStash = new EncryptedStash();
			encryptedStash.readExternal(in);
			encryptedStashes.put(versionId, encryptedStash);
		}
		size = in.readInt();
		paths = new HashMap<>(size);
		while (size-- > 0) {
			double versionId = in.readDouble();
			int nValues = in.readInt();
			EncryptedBucket[] encryptedBuckets = new EncryptedBucket[nValues];
			for (int i = 0; i < nValues; i++) {
				EncryptedBucket bucket = new EncryptedBucket(oramContext.getBucketSize());
				bucket.readExternal(in);
				encryptedBuckets[i] = bucket;
			}
			paths.put(versionId, encryptedBuckets);
		}
		size = in.readInt();
		versionPaths = new HashMap<>(size);
		while (size-- > 0) {
			double outstandingId = in.readDouble();
			int nValues = in.readInt();
			Set<Double> versionIds = new HashSet<>(nValues);
			while (nValues-- > 0){
				versionIds.add(in.readDouble());
			}
			versionPaths.put(outstandingId, versionIds);
		}
	}
}
