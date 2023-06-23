package oram.client.structure;

import java.util.List;
import java.util.Map;

public class StashesAndPaths {

	private final Map<Double, Stash> stashes;
	private final Map<Double, Bucket[]> paths;
	private final Map<Double, List<Double>> snapIdsToOutstanding;

	public StashesAndPaths(Map<Double, Stash> stashes, Map<Double, Bucket[]> paths, Map<Double, List<Double>> snapIdsToOutstanding) {
		this.stashes = stashes;
		this.paths = paths;
		this.snapIdsToOutstanding = snapIdsToOutstanding;
	}

	public Map<Double, Stash> getStashes() {
		return stashes;
	}

	public Map<Double, Bucket[]> getPaths() {
		return paths;
	}

	public Map<Double, List<Double>> getSnapIdsToOutstanding() {
		return snapIdsToOutstanding;
	}
}
