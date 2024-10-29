package oram.client.metadata;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PartialTreeWithDuplicatedBlocks implements RawCustomExternalizable {
	private final Map<Integer, Set<Integer>> locations; // <address, locations>
	private final Map<Integer, Integer> contentVersions; // <address, content version>
	private final Map<Integer, Integer> locationVersions; // <address, location version>

	public PartialTreeWithDuplicatedBlocks() {
		this.locations = new HashMap<>();
		this.contentVersions = new HashMap<>();
		this.locationVersions = new HashMap<>();
	}

	public Map<Integer, Set<Integer>> getLocations() {
		return locations;
	}

	public Map<Integer, Integer> getContentVersions() {
		return contentVersions;
	}

	public Map<Integer, Integer> getLocationVersions() {
		return locationVersions;
	}

	public void put(int address, int location, int contentVersion, int locationVersion) {
		Set<Integer> locations = this.locations.computeIfAbsent(address, k -> new HashSet<>());
		if (contentVersions.containsKey(address)
				&& (contentVersions.get(address) != contentVersion || locationVersions.get(address) != locationVersion)) {
			locations.clear();
		}
		locations.add(location);
		contentVersions.put(address, contentVersion);
		locationVersions.put(address, locationVersion);
	}

	public void remove(int address, int location, int contentVersion, int locationVersion) {
		Set<Integer> locations = this.locations.get(address);
		if (locations != null && contentVersions.get(address) == contentVersion &&
				locationVersions.get(address) == locationVersion) {
			locations.remove(location);
			if (locations.isEmpty()) {
				this.locations.remove(address);
				this.contentVersions.remove(address);
				this.locationVersions.remove(address);
			}
		}
	}

	public void reset() {
		locations.clear();
		contentVersions.clear();
		locationVersions.clear();
	}

	@Override
	public int getSerializedSize() {
		int size = 1;
		for (Set<Integer> value : locations.values()) {
			size += 4 + value.size();
		}
		return size * Integer.BYTES;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;
		ORAMUtils.serializeInteger(locations.size(), output, offset);
		offset += 4;

		int[] orderedAddresses = ORAMUtils.convertSetIntoOrderedArray(locations.keySet());

		for (int address : orderedAddresses) {
			ORAMUtils.serializeInteger(address, output, offset);
			offset += 4;

			Set<Integer> locations = this.locations.get(address);
			ORAMUtils.serializeInteger(locations.size(), output, offset);
			offset += 4;

			int[] orderedLocations = ORAMUtils.convertSetIntoOrderedArray(locations);
			for (int orderedLocation : orderedLocations) {
				ORAMUtils.serializeInteger(orderedLocation, output, offset);
				offset += 4;
			}

			ORAMUtils.serializeInteger(contentVersions.get(address), output, offset);
			offset += 4;

			ORAMUtils.serializeInteger(locationVersions.get(address), output, offset);
			offset += 4;
		}

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;

		int size = ORAMUtils.deserializeInteger(input, offset);
		offset += 4;

		while (size-- > 0) {
			int address = ORAMUtils.deserializeInteger(input, offset);
			offset += 4;

			int locationsSize = ORAMUtils.deserializeInteger(input, offset);
			offset += 4;

			Set<Integer> locations = new HashSet<>(locationsSize);
			while (locationsSize-- > 0) {
				int location = ORAMUtils.deserializeInteger(input, offset);
				offset += 4;
				locations.add(location);
			}

			int contentVersion = ORAMUtils.deserializeInteger(input, offset);
			offset += 4;

			int locationVersion = ORAMUtils.deserializeInteger(input, offset);
			offset += 4;

			this.locations.put(address, locations);
			contentVersions.put(address, contentVersion);
			locationVersions.put(address, locationVersion);
		}
		return offset;
	}

	@Override
	public String toString() {
		return "locations: " + locations
				+ "\tcontent versions: " + contentVersions
				+ "\tlocation versions: " + locationVersions;
	}
}
