package oram.client.structure;

import java.util.ArrayList;
import java.util.Map;

public class DebugSnapshot {
	private final ArrayList<Bucket>[] tree;
	private final Map<Integer, Stash> stashes;

	public DebugSnapshot(ArrayList<Bucket>[] tree, Map<Integer, Stash> stashes) {
		this.tree = tree;
		this.stashes = stashes;
	}

	public ArrayList<Bucket>[] getTree() {
		return tree;
	}

	public Map<Integer, Stash> getStashes() {
		return stashes;
	}
}
