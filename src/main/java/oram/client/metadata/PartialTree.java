package oram.client.metadata;

import oram.client.structure.Block;
import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.HashMap;
import java.util.Map;

public class PartialTree implements RawCustomExternalizable {
	private final Map<Integer, Integer> locations; // <address, locations>
	private final Map<Integer, Integer> contentVersions; // <address, content version>
	private final Map<Integer, Integer> locationVersions; // <address, location version>

	public PartialTree() {
		this.locations = new HashMap<>();
		this.contentVersions = new HashMap<>();
		this.locationVersions = new HashMap<>();
	}

	public Map<Integer, Integer> getLocations() {
		return locations;
	}

	public Map<Integer, Integer> getContentVersions() {
		return contentVersions;
	}

	public Map<Integer, Integer> getLocationVersions() {
		return locationVersions;
	}

	public void put(Block block, int location) {
		locations.put(block.getAddress(), location);
		contentVersions.put(block.getAddress(), block.getContentVersion());
		locationVersions.put(block.getAddress(), block.getLocationVersion());
	}

	public void remove(int address, int contentVersion, int locationVersion) {
		if (locations.containsKey(address) && contentVersions.get(address) == contentVersion &&
				locationVersions.get(address) == locationVersion) {
			this.locations.remove(address);
			this.contentVersions.remove(address);
			this.locationVersions.remove(address);
		}
	}

	public void reset() {
		locations.clear();
		contentVersions.clear();
		locationVersions.clear();
	}

	@Override
	public int getSerializedSize() {
		return Integer.BYTES * (1 + locations.size() * 4);
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

			ORAMUtils.serializeInteger(locations.get(address), output, offset);
			offset += 4;

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

			int location = ORAMUtils.deserializeInteger(input, offset);
			offset += 4;

			int contentVersion = ORAMUtils.deserializeInteger(input, offset);
			offset += 4;

			int locationVersion = ORAMUtils.deserializeInteger(input, offset);
			offset += 4;

			locations.put(address, location);
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
