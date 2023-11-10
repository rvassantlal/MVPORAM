package oram.server.structure;

public class ORAMClientContext {
	private final int[] outstandingVersion;
	private final int newVersionId;
	private final OutstandingTree outstandingTree;
	private int pathId;
	private OutstandingPath outstandingPath;

	public ORAMClientContext(int[] outstandingVersions, int newVersionId,
							 OutstandingTree outstandingTree) {
		this.outstandingVersion = outstandingVersions;
		this.newVersionId = newVersionId;
		this.outstandingTree = outstandingTree;
	}

	public int[] getOutstandingVersions() {
		return outstandingVersion;
	}

	public OutstandingTree getOutstandingTree() {
		return outstandingTree;
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

	public void storeOutstandingPath(OutstandingPath outstandingPath) {
		this.outstandingPath = outstandingPath;
	}

	public OutstandingPath getOutstandingPath() {
		return outstandingPath;
	}
}
