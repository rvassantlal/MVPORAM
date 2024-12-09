package oram.client.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PathMap implements RawCustomExternalizable {
	private Map<Integer, Integer> locations;
	private Map<Integer, Integer> writeVersions;
	private Map<Integer, Integer> accessVersions;

	public PathMap() {}

	public PathMap(int size) {
		this.locations = new HashMap<>(size);
		this.writeVersions = new HashMap<>(size);
		this.accessVersions = new HashMap<>(size);
	}

	public Set<Integer> getStoredAddresses() {
		return locations.keySet();
	}

	public int getLocationOf(int address) {
		return locations.getOrDefault(address, ORAMUtils.DUMMY_LOCATION);
	}

	public int getWriteVersionOf(int address) {
		return writeVersions.getOrDefault(address, ORAMUtils.DUMMY_LOCATION);
	}

	public int getAccessVersionOf(int address) {
		return accessVersions.getOrDefault(address, ORAMUtils.DUMMY_LOCATION);
	}

	public void setLocation(int address, int location, int writeLocation, int readLocation) {
		locations.put(address, location);
		writeVersions.put(address, writeLocation);
		accessVersions.put(address, readLocation);
	}

	@Override
	public int getSerializedSize() {
		return (1 + 4 * locations.size()) * Integer.BYTES;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;

		ORAMUtils.serializeInteger(locations.size(), output, offset);
		offset += Integer.BYTES;

		for (Map.Entry<Integer, Integer> entry : locations.entrySet()) {
			int address = entry.getKey();
			int location = entry.getValue();
			int writeVersion = writeVersions.get(address);
			int accessVersion = accessVersions.get(address);

			ORAMUtils.serializeInteger(address, output, offset);
			offset += Integer.BYTES;

			ORAMUtils.serializeInteger(location, output, offset);
			offset += Integer.BYTES;

			ORAMUtils.serializeInteger(writeVersion, output, offset);
			offset += Integer.BYTES;

			ORAMUtils.serializeInteger(accessVersion, output, offset);
			offset += Integer.BYTES;
		}

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;

		int size = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		locations = new HashMap<>(size);
		writeVersions = new HashMap<>(size);
		accessVersions = new HashMap<>(size);

		while (size-- > 0) {
			int address = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			int location = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			int writeVersion = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			int accessVersion = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			locations.put(address, location);
			writeVersions.put(address, writeVersion);
			accessVersions.put(address, accessVersion);
		}

		return offset;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		locations.keySet().stream().sorted().forEach(address -> {
			sb.append("(A: ").append(address)
					.append(", L: ").append(locations.get(address))
					.append(", WV: ").append(writeVersions.get(address))
					.append(", AV: ").append(accessVersions.get(address))
					.append(") ");
		});
		return sb.toString();
	}
}
