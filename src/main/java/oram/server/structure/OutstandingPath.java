package oram.server.structure;

import java.util.HashMap;
import java.util.Set;

public class OutstandingPath {
	private final HashMap<Integer, Set<BucketSnapshot>> outstandingPath;
	private int totalNumberOfBuckets;

	public OutstandingPath(int pathSize) {
		this.outstandingPath = new HashMap<>(pathSize);
	}

	public Set<BucketSnapshot> getLocation(Integer location) {
		return outstandingPath.get(location);
	}

	public void storeLocation(Integer location, Set<BucketSnapshot> outstandingBuckets) {
		outstandingPath.put(location, outstandingBuckets);
		totalNumberOfBuckets += outstandingBuckets.size();
	}

	public int getTotalNumberOfBuckets() {
		return totalNumberOfBuckets;
	}
}
