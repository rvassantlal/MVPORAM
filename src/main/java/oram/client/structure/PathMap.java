package oram.client.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PathMap implements RawCustomExternalizable {
	private Map<Integer, Integer> locations;
	private Map<Integer, Integer> versions;
	private Map<Integer, Integer> accesses;

	public PathMap() {}

	public PathMap(int size) {
		this.locations = new HashMap<>(size);
		this.versions = new HashMap<>(size);
		this.accesses = new HashMap<>(size);
	}

	public Set<Integer> getStoredAddresses() {
		return locations.keySet();
	}

	public int getLocation(int address) {
		return locations.getOrDefault(address, ORAMUtils.DUMMY_LOCATION);
	}

	public int getVersion(int address) {
		return versions.getOrDefault(address, ORAMUtils.DUMMY_LOCATION);
	}

	public int getAccess(int address) {
		return accesses.getOrDefault(address, ORAMUtils.DUMMY_LOCATION);
	}

	public void setLocation(int address, int location, int version, int access) {
		locations.put(address, location);
		versions.put(address, version);
		accesses.put(address, access);
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
			int version = versions.get(address);
			int access = accesses.get(address);

			ORAMUtils.serializeInteger(address, output, offset);
			offset += Integer.BYTES;

			ORAMUtils.serializeInteger(location, output, offset);
			offset += Integer.BYTES;

			ORAMUtils.serializeInteger(version, output, offset);
			offset += Integer.BYTES;

			ORAMUtils.serializeInteger(access, output, offset);
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
		versions = new HashMap<>(size);
		accesses = new HashMap<>(size);

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
			versions.put(address, writeVersion);
			accesses.put(address, accessVersion);
		}

		return offset;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		locations.keySet().stream().sorted().forEach(address -> {
			sb.append("(ADDR: ").append(address)
					.append(", L: ").append(locations.get(address))
					.append(", V: ").append(versions.get(address))
					.append(", A: ").append(accesses.get(address))
					.append(") ");
		});
		return sb.toString();
	}
}
