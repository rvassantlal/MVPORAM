package oram.client.structure;

import java.util.Arrays;
import java.util.Map;

public class StashesAndPaths {

	private final Map<Integer, Stash> stashes;
	private final Bucket[] paths;

	public StashesAndPaths(Map<Integer, Stash> stashes, Bucket[] paths) {
		this.stashes = stashes;
		this.paths = paths;
	}

	public Map<Integer, Stash> getStashes() {
		return stashes;
	}

	public Bucket[] getPaths() {
		return paths;
	}

	@Override
	public String toString() {
		return "StashesAndPaths{" +
				"stashes=" + stashes +
				", paths=" + Arrays.deepToString(paths) + '}';
	}
}
