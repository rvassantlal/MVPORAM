package oram.client.structure;

import java.util.Map;

public class PositionMaps {
	private final int newVersionId;
	private final Map<Integer, PathMap> pathMaps;

	public PositionMaps(int newVersionId, Map<Integer, PathMap> pathMaps) {
		this.newVersionId = newVersionId;
		this.pathMaps = pathMaps;
	}

	public Map<Integer, PathMap> getPathMaps() {
		return pathMaps;
	}

	public int getNewVersionId() {
		return newVersionId;
	}
}
