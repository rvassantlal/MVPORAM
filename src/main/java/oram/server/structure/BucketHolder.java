package oram.server.structure;

import java.util.*;

public class BucketHolder {
	private final ArrayList<BucketSnapshot> outstandingBuckets;
	private final Set<BucketSnapshot> outstandingBucketsVersions;
	private final Map<Integer, EncryptedBucket> getBucketsBuffer;

	public BucketHolder() {
		this.outstandingBuckets = new ArrayList<>();
		this.outstandingBucketsVersions = new HashSet<>();
		this.getBucketsBuffer = new HashMap<>();
	}

	public void addSnapshot(BucketSnapshot snapshot, Set<BucketSnapshot> outstandingVersions,
							Set<Integer> globalOutstandingVersions) {
		outstandingBuckets.removeIf(outstandingVersions::contains);

		outstandingBuckets.add(snapshot);

		outstandingBucketsVersions.clear();
		outstandingBucketsVersions.addAll(outstandingBuckets);
	}

	public Set<BucketSnapshot> getOutstandingBucketsVersions() {
		return outstandingBucketsVersions;
	}

	public EncryptedBucket[] getBuckets(Set<BucketSnapshot> outstandingTree) {
		getBucketsBuffer.clear();
		/*for (BucketSnapshot outstandingBucket : outstandingBuckets) {
			if (outstandingTree.contains(outstandingBucket.getVersionId())) {
				getBucketsBuffer.add(outstandingBucket.getBucket());
			}
		}*/
		int[] versions = new int[outstandingTree.size()];
		int k = 0;
		for (BucketSnapshot bucketSnapshot : outstandingTree) {
			getBucketsBuffer.put(bucketSnapshot.getVersionId(), bucketSnapshot.getBucket());
			versions[k++] = bucketSnapshot.getVersionId();
		}
		Arrays.sort(versions);
		EncryptedBucket[] result = new EncryptedBucket[getBucketsBuffer.size()];
		for (int i = 0; i < versions.length; i++) {
			result[i] = getBucketsBuffer.get(versions[i]);

		}

		return result;
	}

	public int size() {
		return outstandingBuckets.size();
	}
}
