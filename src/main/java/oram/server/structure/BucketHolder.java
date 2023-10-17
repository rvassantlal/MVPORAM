package oram.server.structure;

import java.util.*;

public class BucketHolder {
	private final ArrayList<BucketSnapshot> outstandingBuckets;

	public BucketHolder() {
		this.outstandingBuckets = new ArrayList<>();
	}

	public void addSnapshot(BucketSnapshot snapshot, Set<BucketSnapshot> outstandingVersions) {
		outstandingBuckets.removeIf(outstandingVersions::contains);

		outstandingBuckets.add(snapshot);
	}

	public ArrayList<BucketSnapshot> getOutstandingBucketsVersions() {
		return outstandingBuckets;
	}

	public int size() {
		return outstandingBuckets.size();
	}
}
