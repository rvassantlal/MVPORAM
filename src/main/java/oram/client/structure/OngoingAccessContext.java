package oram.client.structure;

import oram.utils.ORAMUtils;
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
	private PathMap pathMap;
	private byte[] oldContent;
	private int substitutedBlockAddress;
	private int accessedAddressBucket;
	private PositionMaps oldPositionMaps;

	public OngoingAccessContext(int address, Operation operation, byte[] newContent) {
		this.address = address;
		this.operation = operation;
		this.newContent = newContent;
		this.isRealAccess = true;
		this.accessedPathId = ORAMUtils.DUMMY_LOCATION;
		this.accessedAddressNewBucketLocation = ORAMUtils.DUMMY_LOCATION;
		this.substitutedBlockNewBucketLocation = ORAMUtils.DUMMY_LOCATION;
		this.newVersionId = ORAMUtils.DUMMY_VERSION;
		this.substitutedBlockAddress = ORAMUtils.DUMMY_ADDRESS;
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

	public void setPathMap(PathMap positionMap) {
		this.pathMap = positionMap;
	}

	public PathMap getPathMap() {
		return pathMap;
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

	public void setSubstitutedBlockAddress(int substitutedBlockAddress) {
		this.substitutedBlockAddress = substitutedBlockAddress;
	}

	public int getSubstitutedBlockAddress() {
		return substitutedBlockAddress;
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
