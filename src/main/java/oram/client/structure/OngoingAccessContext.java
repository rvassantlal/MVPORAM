package oram.client.structure;

import oram.utils.Operation;

public class OngoingAccessContext {
	private final int address;
	private final Operation operation;
	private final byte[] newContent;
	private PositionMap mergedPositionMap;
	private int accessedPathId;
	private Stash mergedStash;
	private int accessedAddressNewBucketLocation;
	private int substitutedBlockNewBucketLocation;
	private boolean isRealAccess;
	private int newVersionId;
	private PositionMap updatedPositionMap;
	private byte[] oldContent;

	public OngoingAccessContext(int address, Operation operation, byte[] newContent) {
		this.address = address;
		this.operation = operation;
		this.newContent = newContent;
		this.isRealAccess = true;
	}

	public void setMergedPositionMap(PositionMap mergedPositionMap) {
		this.mergedPositionMap = mergedPositionMap;
	}

	public void setAccessedPathId(int pathId) {
		this.accessedPathId = pathId;
	}

	public void setMergedStash(Stash mergedStash) {
		this.mergedStash = mergedStash;
	}

	public void setAccessedAddressNewBucketLocation(int newBucketLocation) {
		this.accessedAddressNewBucketLocation = newBucketLocation;
	}

	public void setSubstitutedBlockNewBucketLocation(int substitutionNewBucketLocation) {
		this.substitutedBlockNewBucketLocation = substitutionNewBucketLocation;
	}

	public void setIsRealAccess(boolean isRealAccess) {
		this.isRealAccess = isRealAccess;
	}


	public boolean isRealAccess() {
		return isRealAccess;
	}

	public int getAddress() {
		return address;
	}

	public PositionMap getMergedPositionMap() {
		return mergedPositionMap;
	}

	public int getAccessedPathId() {
		return accessedPathId;
	}

	public Stash getMergedStash() {
		return mergedStash;
	}

	public int getAccessedAddressNewBucketLocation() {
		return accessedAddressNewBucketLocation;
	}

	public int getSubstitutedBlockNewBucketLocation() {
		return substitutedBlockNewBucketLocation;
	}

	public Operation getOperation() {
		return operation;
	}

	public void setNewVersionId(int newVersionId) {
		this.newVersionId = newVersionId;
	}

	public int getNewVersionId() {
		return newVersionId;
	}

	public void setUpdatedPositionMap(PositionMap positionMap) {
		this.updatedPositionMap = positionMap;
	}

	public PositionMap getUpdatedPositionMap() {
		return updatedPositionMap;
	}

	public byte[] getNewContent() {
		return newContent;
	}

	public void setOldContent(byte[] content) {
		this.oldContent = content;
	}

	public byte[] getOldContent() {
		return oldContent;
	}
}
