package oram.client.structure;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class StashesAndPaths {

	private final Map<Double, Stash> stashes;
	private final Map<Double, Bucket[]> paths;
	private final Map<Double, Set<Double>> versionPaths;

	public StashesAndPaths(Map<Double, Stash> stashes, Map<Double, Bucket[]> paths,
						   Map<Double, Set<Double>> versionPaths) {
		this.stashes = stashes;
		this.paths = paths;
		this.versionPaths = versionPaths;
	}

	public Map<Double, Stash> getStashes() {
		return stashes;
	}

	public Map<Double, Bucket[]> getPaths() {
		return paths;
	}

	public Map<Double, Set<Double>> getVersionPaths() {
		return versionPaths;
	}
}
