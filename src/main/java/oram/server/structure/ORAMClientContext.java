package oram.server.structure;

import java.util.HashMap;
import java.util.Set;

public class ORAMClientContext {
	private final int[] outstandingVersion;
	private final EncryptedStash[] outstandingStashes;
	private final int newVersionId;
	private HashMap<Integer, Set<Integer>> outstandingTree;

	private int pathId;

	public ORAMClientContext(int[] outstandingVersions, EncryptedStash[] outstandingStashes, int newVersionId,
							 HashMap<Integer, Set<Integer>> outstandingTree) {
		this.outstandingVersion = outstandingVersions;
		this.outstandingStashes = outstandingStashes;
		this.newVersionId = newVersionId;
		this.outstandingTree = outstandingTree;
	}

	public int[] getOutstandingVersions() {
		return outstandingVersion;
	}

	public EncryptedStash[] getOutstandingStashes() {
		return outstandingStashes;
	}

	public HashMap<Integer, Set<Integer>> getOutstandingTree() {
		return outstandingTree;
	}

	public void setOutstandingTree(HashMap<Integer, Set<Integer>> outstandingTree) {
		this.outstandingTree = outstandingTree;
	}

	public int getNewVersionId() {
		return newVersionId;
	}

	public void setPathId(int pathId) {
		this.pathId = pathId;
	}

	public int getPathId() {
		return pathId;
	}
}
