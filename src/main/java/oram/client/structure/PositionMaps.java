package oram.client.structure;

import java.util.Map;

public class PositionMaps {
	private final int newVersionId;
	private final Map<Integer, PositionMap> positionMaps;
	private final Map<Integer, EvictionMap> evictionMap;
	private final int[] outstandingVersions;
	private final Map<Integer, int[]> allOutstandingVersions;

	public PositionMaps(int newVersionId, Map<Integer, PositionMap> positionMaps, Map<Integer, EvictionMap> evictionMap,
						int[] outstandingVersions, Map<Integer, int[]> allOutstandingVersions) {
		this.newVersionId = newVersionId;
		this.positionMaps = positionMaps;
		this.evictionMap = evictionMap;
		this.outstandingVersions = outstandingVersions;
		this.allOutstandingVersions = allOutstandingVersions;
	}

	public Map<Integer, PositionMap> getPositionMaps() {
		return positionMaps;
	}

	public int getNewVersionId() {
		return newVersionId;
	}

	public Map<Integer, EvictionMap> getEvictionMap() {
		return evictionMap;
	}

	public Map<Integer, int[]> getAllOutstandingVersions() {
		return allOutstandingVersions;
	}

	public int[] getOutstandingVersions() {
		return outstandingVersions;
	}
}
