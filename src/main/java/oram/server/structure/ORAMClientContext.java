package oram.server.structure;

public class ORAMClientContext {
	private final double[] versionIds;

	public ORAMClientContext(double[] versionIds) {
		this.versionIds = versionIds;
	}

	public double[] getVersionIds() {
		return versionIds;
	}
}
