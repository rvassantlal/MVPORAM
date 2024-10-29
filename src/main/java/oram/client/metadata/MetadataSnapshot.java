package oram.client.metadata;

import java.util.Map;

public class MetadataSnapshot {

	private final Map<Integer, Map<Integer, Integer>> locations;
	private final Map<Integer, Integer> contentVersions;
	private final Map<Integer, Integer> locationVersions;

	public MetadataSnapshot(Map<Integer, Map<Integer, Integer>> locations, Map<Integer, Integer> contentVersions,
							Map<Integer, Integer> locationVersions) {
		this.locations = locations;
		this.contentVersions = contentVersions;
		this.locationVersions = locationVersions;
	}

	public Map<Integer, Map<Integer, Integer>> getLocations() {
		return locations;
	}

	public Map<Integer, Integer> getContentVersions() {
		return contentVersions;
	}

	public Map<Integer, Integer> getLocationVersions() {
		return locationVersions;
	}
}
