package oram.benchmark;

import oram.client.structure.PathMaps;
import oram.client.structure.Stash;
import oram.utils.ORAMUtils;
import oram.utils.Operation;

public class OngoingDirectAccessContext {
	private final Operation op;
	private final int address;
	private final byte[] newContent;
	private int accessedPathId;
	private boolean isRealAccess;
	private int operationSequence;
	private int accessedAddressBucket;
	private PathMaps pathMapsHistory;
	private byte[] oldData;
	private Stash mergedStash;

	public OngoingDirectAccessContext(Operation op, int address, byte[] newContent) {
		this.op = op;
		this.address = address;
		this.newContent = newContent;
		this.isRealAccess = true;
		this.accessedPathId = ORAMUtils.DUMMY_LOCATION;
		this.operationSequence = ORAMUtils.DUMMY_VERSION;
	}

	public Operation getOperation() {
		return op;
	}

	public int getAddress() {
		return address;
	}

	public byte[] getNewContent() {
		return newContent;
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

	public void setOldData(byte[] oldData) {
		this.oldData = oldData;
	}

	public byte[] getOldData() {
		return oldData;
	}

	public void setMergedStash(Stash mergedStash) {
		this.mergedStash = mergedStash;
	}

	public Stash getMergedStash() {
		return mergedStash;
	}
}
