package oram.server.structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class BucketHolder {
	private final ArrayList<BucketSnapshot> outstandingBuckets;
	private final Set<Integer> outstandingBucketsVersions;
	private final ArrayList<EncryptedBucket> getBucketsBuffer;

	public BucketHolder() {
		this.outstandingBuckets = new ArrayList<>();
		this.outstandingBucketsVersions = new HashSet<>();
		this.getBucketsBuffer = new ArrayList<>();
	}

	public void addSnapshot(BucketSnapshot snapshot, Set<Integer> outstandingVersions,
							Set<Integer> globalOutstandingVersions) {
		outstandingBuckets.removeIf(outstandingBucket ->
				outstandingVersions.contains(outstandingBucket.getVersionId())
				&& !globalOutstandingVersions.contains(outstandingBucket.getVersionId()));

		outstandingBuckets.add(snapshot);

		outstandingBucketsVersions.clear();
		for (BucketSnapshot outstandingBucket : outstandingBuckets) {
			outstandingBucketsVersions.add(outstandingBucket.getVersionId());
		}
	}

	public Set<Integer> getOutstandingBucketsVersions() {
		return outstandingBucketsVersions;
	}

	public EncryptedBucket[] getBuckets(Set<Integer> outstandingTree) {
		getBucketsBuffer.clear();
		for (BucketSnapshot outstandingBucket : outstandingBuckets) {
			if (outstandingTree.contains(outstandingBucket.getVersionId())) {
				getBucketsBuffer.add(outstandingBucket.getBucket());
			}
		}
		EncryptedBucket[] result = new EncryptedBucket[getBucketsBuffer.size()];
		for (int i = 0; i < getBucketsBuffer.size(); i++) {
			result[i] = getBucketsBuffer.get(i);
		}

		return result;
	}

	public int size() {
		return outstandingBuckets.size();
	}
}
