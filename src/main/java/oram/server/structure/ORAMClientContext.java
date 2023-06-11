package oram.server.structure;

public class ORAMClientContext {
	private final OramSnapshot[] outstandingVersion;

	public ORAMClientContext(OramSnapshot[] versions) {
		this.outstandingVersion = versions;
	}

	public OramSnapshot[] getOutstandingVersions() {
		return outstandingVersion;
	}
}
