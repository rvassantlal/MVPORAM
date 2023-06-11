package oram.client.structure;

import java.util.Map;

public class StashesAndPaths {

	private final Map<Double, Stash> stashes;
	private final Map<Double, Bucket[]> paths;

	public StashesAndPaths(Map<Double, Stash> stashes, Map<Double, Bucket[]> paths) {
		this.stashes = stashes;
		this.paths = paths;
	}

	public Map<Double, Stash> getStashes() {
		return stashes;
	}

	public Map<Double, Bucket[]> getPaths() {
		return paths;
	}
}
