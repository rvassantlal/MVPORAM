package oram.client.structure;

import java.util.Map;

public class PathMaps {
	private final int operationSequence;
	private final Map<Integer, PathMap> pathMaps;

	public PathMaps(int operationSequence, Map<Integer, PathMap> pathMaps) {
		this.operationSequence = operationSequence;
		this.pathMaps = pathMaps;
	}

	public Map<Integer, PathMap> getPathMaps() {
		return pathMaps;
	}

	public int getOperationSequence() {
		return operationSequence;
	}
}
