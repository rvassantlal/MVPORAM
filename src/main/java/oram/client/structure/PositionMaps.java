package oram.client.structure;

import java.util.Map;

public class PositionMaps {
	private final int newVersionId;
	private final Map<Integer, PathMap> pathMaps;
	private final int[] outstandingVersions;
	private final Map<Integer, int[]> allOutstandingVersions;

	public PositionMaps(int newVersionId, Map<Integer, PathMap> pathMaps,
						int[] outstandingVersions, Map<Integer, int[]> allOutstandingVersions) {
		this.newVersionId = newVersionId;
		this.pathMaps = pathMaps;
		this.outstandingVersions = outstandingVersions;
		this.allOutstandingVersions = allOutstandingVersions;
	}

	public Map<Integer, PathMap> getPathMaps() {
		return pathMaps;
	}

	public int getNewVersionId() {
		return newVersionId;
	}

	public Map<Integer, int[]> getAllOutstandingVersions() {
		return allOutstandingVersions;
	}

	public int[] getOutstandingVersions() {
		return outstandingVersions;
	}
}
