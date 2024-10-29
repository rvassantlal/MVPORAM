package oram.client.structure;

import oram.utils.ORAMUtils;

import java.util.ArrayList;

public class BlockLocation {
	private int contentVersion;
	private int locationVersion;
	private boolean isInTree;
	private ArrayList<Integer> locations;

	public BlockLocation() {
		this.contentVersion = ORAMUtils.DUMMY_VERSION;
		this.locationVersion = ORAMUtils.DUMMY_VERSION;
		this.locations = new ArrayList<>();
	}

	public void addLocation(int location) {
		locations.add(location);
	}

	public void resetLocations() {
		this.locations.clear();
	}

	public int getLocationVersion() {
		return locationVersion;
	}

	public int getContentVersion() {
		return contentVersion;
	}

	public boolean isInTree() {
		return isInTree;
	}

	public void setContentVersion(int contentVersion) {
		this.contentVersion = contentVersion;
	}

	public void setLocationVersion(int locationVersion) {
		this.locationVersion = locationVersion;
	}

	public void setInTree(boolean inTree) {
		isInTree = inTree;
	}

	@Override
	public String toString() {
		return contentVersion + " " + locationVersion + " " + isInTree + " " + locations;
	}
}
