package oram.server.structure;

public class BucketSnapshot implements Comparable<BucketSnapshot> {
	private final int versionId;
	private final EncryptedBucket bucket;

	public BucketSnapshot(int versionId, EncryptedBucket bucket) {
		this.versionId = versionId;
		this.bucket = bucket;
	}

	public int getVersionId() {
		return versionId;
	}

	public EncryptedBucket getBucket() {
		return bucket;
	}

	@Override
	public int hashCode() {
		return versionId;
	}

	@Override
	public int compareTo(BucketSnapshot o) {
		return Integer.compare(versionId, o.versionId);
	}

	@Override
	public String toString() {
		return "{" + versionId + ", " + bucket + "}";
	}
}
