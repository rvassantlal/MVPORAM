package oram.server.structure;

public class ORAMClientContext {
	private final OramSnapshot[] outstandingVersion;
	private final int newVersionId;

	private int pathId;

	public ORAMClientContext(OramSnapshot[] versions, int newVersionId) {
		this.outstandingVersion = versions;
		this.newVersionId = newVersionId;
	}

	public OramSnapshot[] getOutstandingVersions() {
		return outstandingVersion;
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
