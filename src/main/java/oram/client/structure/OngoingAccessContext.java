package oram.client.structure;

import oram.utils.ORAMUtils;

public class OngoingAccessContext {
	private int accessedPathId;
	private boolean isRealAccess;
	private int operationSequence;
	private int accessedAddressBucket;
	private PathMaps pathMapsHistory;

	public OngoingAccessContext() {
		this.isRealAccess = true;
		this.accessedPathId = ORAMUtils.DUMMY_LOCATION;
		this.operationSequence = ORAMUtils.DUMMY_VERSION;
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

	public void setOperationSequence(int operationSequence) {
		this.operationSequence = operationSequence;
	}

	public int getOperationSequence() {
		return operationSequence;
	}

	public void setAccessedAddressBucket(int accessedAddressBucket) {
		this.accessedAddressBucket = accessedAddressBucket;
	}

	public int getAccessedAddressBucket() {
		return accessedAddressBucket;
	}

	public void setPathMapsHistory(PathMaps pathMapsHistory) {
		this.pathMapsHistory = pathMapsHistory;
	}

	public PathMaps getPathMapsHistory() {
		return pathMapsHistory;
	}
}
