package oram.client.metadata;

import oram.utils.ORAMUtils;

import java.util.*;

public class OutstandingGraph {
	private final Map<Integer, int[]> outstandingVersions;
	private final Queue<int[]> queue;
	private final Set<Integer> visitedOutstandingVersions;

	public OutstandingGraph() {
		this.outstandingVersions = new HashMap<>();
		this.queue = new ArrayDeque<>();
		this.visitedOutstandingVersions = new HashSet<>();
	}

	public Map<Integer, int[]> getOutstandingVersions() {
		return outstandingVersions;
	}

	public void addOutstandingVersions(Map<Integer, int[]> newOutstandingVersions) {
		outstandingVersions.putAll(newOutstandingVersions);
	}

	public boolean doesOverrides(int currentVersion, int oldVersion) {
		queue.clear();
		visitedOutstandingVersions.clear();
		queue.add(outstandingVersions.get(currentVersion));
		while (!queue.isEmpty()) {
			int[] versions = queue.poll();
			for (int version : versions) {
				if (version == ORAMUtils.DUMMY_VERSION) {
					return false;
				}
				if (version == oldVersion) {
					return true;
				}
				if (version > oldVersion) {
					int[] newVersions = outstandingVersions.get(version);
					int hash = ORAMUtils.computeHashCode(newVersions);
					if (!visitedOutstandingVersions.contains(hash)) {
						queue.add(newVersions);
						visitedOutstandingVersions.add(hash);
					}
				}
			}
		}
		return false;
	}

	public boolean doesOverrides(int currentVersion, Set<Integer> limitOutstandingVersions) {
		queue.clear();
		visitedOutstandingVersions.clear();
		queue.add(outstandingVersions.get(currentVersion));
		int minLimitVersion = Collections.min(limitOutstandingVersions);
		while (!queue.isEmpty()) {
			int[] versions = queue.poll();
			for (int version : versions) {
				if (version == ORAMUtils.DUMMY_VERSION) {
					return false;
				}
				if (limitOutstandingVersions.contains(version)) {
					return true;
				}
				if (version > minLimitVersion) {
					int[] newVersions = outstandingVersions.get(version);
					int hash = ORAMUtils.computeHashCode(newVersions);
					if (!visitedOutstandingVersions.contains(hash)) {
						queue.add(newVersions);
						visitedOutstandingVersions.add(hash);
					}
				}
			}
		}
		return false;
	}
}
