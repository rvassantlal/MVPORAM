package oram.client.structure;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class StashesAndPaths {

	private final Map<Integer, Stash> stashes;
	private final Map<Integer, Bucket[]> paths;
	private final Map<Integer, Set<Integer>> versionPaths;

	public StashesAndPaths(Map<Integer, Stash> stashes, Map<Integer, Bucket[]> paths,
						   Map<Integer, Set<Integer>> versionPaths) {
		this.stashes = stashes;
		this.paths = paths;
		this.versionPaths = versionPaths;
	}

	public Map<Integer, Stash> getStashes() {
		return stashes;
	}

	public Map<Integer, Bucket[]> getPaths() {
		return paths;
	}

	public Map<Integer, Set<Integer>> getVersionPaths() {
		return versionPaths;
	}

	@Override
	public String toString() {
		return "StashesAndPaths{" +
				"stashes=" + stashes.entrySet().stream().map(k -> k.getKey().toString() + " , " +
				k.getValue().toString()).reduce(String::concat) +
				", paths=" + paths.entrySet().stream().map(j -> j.getKey().toString() + " , " +
				Arrays.deepToString(j.getValue())).reduce(String::concat) +
				", versionPaths=" + versionPaths.entrySet().stream().map(j -> j.getKey().toString() + " , " +
				j.getValue().toString()).reduce(String::concat) +
				'}';
	}
}
