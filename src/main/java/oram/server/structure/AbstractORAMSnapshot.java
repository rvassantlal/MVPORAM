package oram.server.structure;

public abstract class AbstractORAMSnapshot {
	private final double versionId;
	protected final ORAMContext oramContext;
	protected EncryptedPositionMap encryptedPositionMap;
	protected EncryptedStash encryptedStash;

	protected AbstractORAMSnapshot(double versionId, ORAMContext oramContext) {
		this.versionId = versionId;
		this.oramContext = oramContext;
	}

	public double getVersionId() {
		return versionId;
	}

	public EncryptedPositionMap getEncryptedPositionMap() {
		return encryptedPositionMap;
	}

	public EncryptedStash getEncryptedStash() {
		return encryptedStash;
	}

	public abstract EncryptedPath getPath(byte pathId);
}
