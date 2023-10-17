package oram.server.structure;

public class ORAMClientContext {
	private final int[] outstandingVersion;
	private final EncryptedStash[] outstandingStashes;
	private final int newVersionId;
	private OutstandingTreeContext outstandingTree;
	private int pathId;

	public ORAMClientContext(int[] outstandingVersions, EncryptedStash[] outstandingStashes, int newVersionId,
							 OutstandingTreeContext outstandingTree) {
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

	public OutstandingTreeContext getOutstandingTree() {
		return outstandingTree;
	}

	public void setOutstandingTree(OutstandingTreeContext outstandingTree) {
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
