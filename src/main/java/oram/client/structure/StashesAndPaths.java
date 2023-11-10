package oram.client.structure;

import java.util.Arrays;

public class StashesAndPaths {

	private final Stash[] stashes;
	private final Bucket[] paths;

	public StashesAndPaths(Stash[] stashes, Bucket[] paths) {
		this.stashes = stashes;
		this.paths = paths;
	}

	public Stash[] getStashes() {
		return stashes;
	}

	public Bucket[] getPaths() {
		return paths;
	}

	@Override
	public String toString() {
		return "StashesAndPaths{" +
				"stashes=" + Arrays.stream(stashes).map(k -> k == null ? "null" : k.toString()).reduce(String::concat) +
				", paths=" + Arrays.deepToString(paths) + '}';
	}
}
