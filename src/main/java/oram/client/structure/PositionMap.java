package oram.client.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.Arrays;

public class PositionMap implements RawCustomExternalizable {
	private int[] locations;
	private int[] accesses;
	private int[] versions;
	private int[] locationUpdateAccesses;

	public PositionMap() {}

	public PositionMap(int size) {
		this.accesses = new int[size];
		this.locations = new int[size];
		this.versions = new int[size];
		this.locationUpdateAccesses = new int[size];
		Arrays.fill(locations, ORAMUtils.DUMMY_LOCATION);
		Arrays.fill(accesses, ORAMUtils.DUMMY_VERSION);
		Arrays.fill(versions, ORAMUtils.DUMMY_VERSION);
		Arrays.fill(locationUpdateAccesses, ORAMUtils.DUMMY_VERSION);
	}

	public int getLocation(int address) {
		if (address < 0 || address >= locations.length) {
			return ORAMUtils.DUMMY_LOCATION;
		}
		return locations[address];
	}

	public int getAccess(int address) {
		if (address < 0 || address >= locations.length) {
			return ORAMUtils.DUMMY_VERSION;
		}
		return accesses[address];
	}

	public int getVersion(int address) {
		if (address < 0 || address >= locations.length) {
			return ORAMUtils.DUMMY_VERSION;
		}
		return versions[address];
	}

	public int getLocationUpdateAccess(int address) {
		if (address < 0 || address >= locations.length) {
			return ORAMUtils.DUMMY_VERSION;
		}
		return locationUpdateAccesses[address];
	}

	public void update(int address, int location, int version, int access, int locationUpdateAccess) {
		locations[address] = location;
		versions[address] = version;
		accesses[address] = access;
		locationUpdateAccesses[address] = locationUpdateAccess;
	}

	@Override
	public int getSerializedSize() {
		return  (1 + locations.length * 4) * Integer.BYTES;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;

		ORAMUtils.serializeInteger(locations.length, output, offset);
		offset += Integer.BYTES;

		for (int i = 0; i < locations.length; i++) {
			ORAMUtils.serializeInteger(locations[i], output, offset);
			offset += Integer.BYTES;

			ORAMUtils.serializeInteger(accesses[i], output, offset);
			offset += Integer.BYTES;

			ORAMUtils.serializeInteger(versions[i], output, offset);
			offset += Integer.BYTES;

			ORAMUtils.serializeInteger(locationUpdateAccesses[i], output, offset);
			offset += Integer.BYTES;
		}

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;

		int size = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		locations = new int[size];
		accesses = new int[size];
		versions = new int[size];
		for (int i = 0; i < size; i++) {
			locations[i] = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			accesses[i] = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			versions[i] = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			locationUpdateAccesses[i] = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;
		}

		return offset;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < locations.length; i++) {
			sb.append("ADDR: ").append(i)
					.append(", L: ").append(locations[i])
					.append(", V: ").append(versions[i])
					.append(", A: ").append(accesses[i])
					.append(", LUA: ").append(locationUpdateAccesses[i]).append("\n");
		}
		return sb.toString();
	}

	public String toStringNonNull() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < locations.length; i++) {
			if (accesses[i] == ORAMUtils.DUMMY_VERSION) {
				continue;
			}
			sb.append("ADDR: ").append(i)
					.append(", L: ").append(locations[i])
					.append(", V: ").append(versions[i])
					.append(", A: ").append(accesses[i])
					.append(", LUA: ").append(locationUpdateAccesses[i]).append("\n");
		}
		return sb.toString();
	}
}
