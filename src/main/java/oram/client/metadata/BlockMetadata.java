package oram.client.metadata;

import oram.utils.ORAMUtils;

public class BlockMetadata {
	private int highestLocation;
	private int highestLocationVersion;
	private int contentVersion;
	private int locationVersion;

	public BlockMetadata() {
		contentVersion = ORAMUtils.DUMMY_VERSION;
		locationVersion = ORAMUtils.DUMMY_VERSION;
		highestLocation = ORAMUtils.DUMMY_LOCATION;
		highestLocationVersion = ORAMUtils.DUMMY_VERSION;
	}

	public BlockMetadata(int contentVersion, int locationVersion) {
		this.contentVersion = contentVersion;
		this.locationVersion = locationVersion;
		this.highestLocation = ORAMUtils.DUMMY_LOCATION;
		this.highestLocationVersion = ORAMUtils.DUMMY_VERSION;
	}

	public int getHighestLocation() {
		return highestLocation;
	}

	public int getHighestLocationVersion() {
		return highestLocationVersion;
	}

	public int getContentVersion() {
		return contentVersion;
	}

	public int getLocationVersion() {
		return locationVersion;
	}

	public void setNewValues(int contentVersion, int locationVersion, int location, int version) {
		this.contentVersion = contentVersion;
		this.locationVersion = locationVersion;
		this.highestLocation = location;
		this.highestLocationVersion = version;
	}

	public BlockMetadata copy() {
		BlockMetadata copy = new BlockMetadata();
		copy.highestLocation = highestLocation;
		copy.highestLocationVersion = highestLocationVersion;
		copy.contentVersion = contentVersion;
		copy.locationVersion = locationVersion;
		return copy;
	}

	public void mergeMetadata(BlockMetadata metadata, int[] outstandingVersions, int pmContentVersion, int pmLocationVersion) {
		if (contentVersion != pmContentVersion || locationVersion != pmLocationVersion) {
			contentVersion = pmContentVersion;
			locationVersion = pmLocationVersion;
			highestLocation = ORAMUtils.DUMMY_LOCATION;
			highestLocationVersion = ORAMUtils.DUMMY_VERSION;
		}
		if (metadata.contentVersion != pmContentVersion || metadata.locationVersion != pmLocationVersion) {
			return;
		}

		boolean isMyHighestVersionOV = false;
		boolean isMetadataHighestVersionOV = false;
		for (int outstandingVersion : outstandingVersions) {
			if (highestLocationVersion == outstandingVersion) {
				isMyHighestVersionOV = true;
			}
			if (metadata.highestLocationVersion == outstandingVersion) {
				isMetadataHighestVersionOV = true;
			}
		}

		if (isMyHighestVersionOV && isMetadataHighestVersionOV) {
			if (metadata.highestLocation > highestLocation || (metadata.highestLocation == highestLocation
					&& metadata.highestLocationVersion > highestLocationVersion)) {
				highestLocation = metadata.highestLocation;
				highestLocationVersion = metadata.highestLocationVersion;
			}
		} else if (isMyHighestVersionOV) {

		} else if (isMetadataHighestVersionOV || highestLocationVersion == ORAMUtils.DUMMY_VERSION) {
			highestLocation = metadata.highestLocation;
			highestLocationVersion = metadata.highestLocationVersion;
		} else {

		}
	}

	public void moveBlockToPath(int location, int updateVersion, int contentVersion, int locationVersion) {
		if (this.contentVersion != contentVersion || this.locationVersion != locationVersion) {
			this.contentVersion = contentVersion;
			this.locationVersion = locationVersion;
			highestLocation = ORAMUtils.DUMMY_LOCATION;
			highestLocationVersion = ORAMUtils.DUMMY_VERSION;
		}
		if (highestLocation < location) {
			highestLocation = location;
			highestLocationVersion = updateVersion;
		} else if (highestLocation == location && highestLocationVersion < updateVersion) {
			highestLocationVersion = updateVersion;
		}
	}

	public void removeBlockFromPath(int location, int updateVersion, int contentVersion, int locationVersion) {
		if (this.contentVersion != contentVersion || this.locationVersion != locationVersion) {
			this.contentVersion = contentVersion;
			this.locationVersion = locationVersion;
			highestLocation = ORAMUtils.DUMMY_LOCATION;
			highestLocationVersion = ORAMUtils.DUMMY_VERSION;
		}

		if (highestLocation == location) {
			highestLocation = ORAMUtils.DUMMY_LOCATION;
			highestLocationVersion = updateVersion;
		}
	}

	public boolean isInTree() {
		return highestLocation != ORAMUtils.DUMMY_LOCATION;
	}

	@Override
	public String toString() {
		return "(CV: " + contentVersion + " | LV: " + locationVersion + " | HL: "
				+ highestLocation + " | HLV: " + highestLocationVersion + ")";
	}

	public boolean isInHighestLocation(int location) {
		return highestLocation == location;
	}
}
