package oram.client.structure;

import oram.utils.ORAMUtils;

public class OngoingAccessContext {
	private int accessedPathId;
	private boolean isRealAccess;
	private int newVersionId;
	private int accessedAddressBucket;
	private PositionMaps oldPositionMaps;

	public OngoingAccessContext() {
		this.isRealAccess = true;
		this.accessedPathId = ORAMUtils.DUMMY_LOCATION;
		this.newVersionId = ORAMUtils.DUMMY_VERSION;
	}

	public void setAccessedPathId(int pathId) {
		this.accessedPathId = pathId;
	}

	public int getAccessedPathId() {
		return accessedPathId;
	}


	public void setIsRealAccess(boolean isRealAccess) {
		this.isRealAccess = isRealAccess;
	}


	public boolean isRealAccess() {
		return isRealAccess;
	}

	public void setNewVersionId(int newVersionId) {
		this.newVersionId = newVersionId;
	}

	public int getNewVersionId() {
		return newVersionId;
	}

	public void setAccessedAddressBucket(int accessedAddressBucket) {
		this.accessedAddressBucket = accessedAddressBucket;
	}

	public int getAccessedAddressBucket() {
		return accessedAddressBucket;
	}

	public void setOldPositionMaps(PositionMaps oldPositionMaps) {
		this.oldPositionMaps = oldPositionMaps;
	}

	public PositionMaps getOldPositionMaps() {
		return oldPositionMaps;
	}
}
