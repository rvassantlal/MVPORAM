package oram.server.structure;

import java.util.*;

public class BucketHolder {
	private final ArrayList<BucketSnapshot> outstandingBuckets;

	public BucketHolder() {
		this.outstandingBuckets = new ArrayList<>();
	}

	public BucketHolder(ArrayList<BucketSnapshot> outstandingBucketsVersions) {
		this.outstandingBuckets = new ArrayList<>(outstandingBucketsVersions);
	}

	public void update(ArrayList<BucketSnapshot> outstandingLocation) {
		outstandingBuckets.clear();
		outstandingBuckets.addAll(outstandingLocation);
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
