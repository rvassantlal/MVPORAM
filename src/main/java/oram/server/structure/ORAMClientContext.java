package oram.server.structure;

public class ORAMClientContext {
	private final OramSnapshot[] outstandingVersion;
	private final double newVersionId;

	public ORAMClientContext(OramSnapshot[] versions, double newVersionId) {
		this.outstandingVersion = versions;
		this.newVersionId = newVersionId;
	}

	public OramSnapshot[] getOutstandingVersions() {
		return outstandingVersion;
	}

	public double getNewVersionId() {
		return newVersionId;
	}
}
