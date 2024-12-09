package oram.client.structure;

import oram.client.metadata.OutstandingGraph;
import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.Arrays;

public class PositionMap implements RawCustomExternalizable {
	private int[] locations;
	private int[] accessVersions;
	private int[] writeVersions;
	private int[] locationUpdateVersions;

	public PositionMap() {}

	public PositionMap(int size) {
		this.accessVersions = new int[size];
		this.locations = new int[size];
		this.writeVersions = new int[size];
		this.locationUpdateVersions = new int[size];
		Arrays.fill(locations, ORAMUtils.DUMMY_LOCATION);
		Arrays.fill(accessVersions, ORAMUtils.DUMMY_VERSION);
		Arrays.fill(writeVersions, ORAMUtils.DUMMY_VERSION);
		Arrays.fill(locationUpdateVersions, ORAMUtils.DUMMY_VERSION);
	}

	public int getLocationOf(int address) {
		if (address < 0 || address >= locations.length) {
			return ORAMUtils.DUMMY_LOCATION;
		}
		return locations[address];
	}

	public void setLocationOf(int address, int location) {
		if (address < 0 || address >= locations.length) {
			return;
		}
		locations[address] = location;
	}

	public int getAccessVersionOf(int address) {
		if (address < 0 || address >= locations.length) {
			return ORAMUtils.DUMMY_VERSION;
		}
		return accessVersions[address];
	}

	public void setAccessVersionOf(int address, int newLocationVersion) {
		if (address < 0 || address >= locations.length) {
			return;
		}
		accessVersions[address] = newLocationVersion;
	}

	public int getWriteVersionOf(int address) {
		if (address < 0 || address >= locations.length) {
			return ORAMUtils.DUMMY_VERSION;
		}
		return writeVersions[address];
	}

	public void setWriteVersionOf(int address, int newContentVersion) {
		if (address < 0 || address >= locations.length) {
			return;
		}
		writeVersions[address] = newContentVersion;
	}

	public int getLocationUpdateVersion(int address) {
		if (address < 0 || address >= locations.length) {
			return ORAMUtils.DUMMY_VERSION;
		}
		return locationUpdateVersions[address];
	}

	public void setLocationUpdateVersions(int address, int newLocationUpdateVersion) {
		if (address < 0 || address >= locations.length) {
			return;
		}
		locationUpdateVersions[address] = newLocationUpdateVersion;
	}

	public void update(int address, int location, int writeVersion, int accessVersion, int locationUpdateVersion) {
		locations[address] = location;
		writeVersions[address] = writeVersion;
		accessVersions[address] = accessVersion;
		locationUpdateVersions[address] = locationUpdateVersion;
	}

	public void merge(PositionMap outstandingPositionMap, OutstandingGraph outstandingGraph) {
		int size = locations.length;
		for (int address = 0; address < size; address++) {
			int currentLocation = locations[address];
			int currentWriteVersion = writeVersions[address];
			int currentAccessVersion = accessVersions[address];
			int currentLocationUpdateVersion = locationUpdateVersions[address];
			int outstandingLocation = outstandingPositionMap.locations[address];
			int outstandingWriteVersion = outstandingPositionMap.writeVersions[address];
			int outstandingAccessVersion = outstandingPositionMap.accessVersions[address];
			int outstandingLocationUpdateVersion = outstandingPositionMap.locationUpdateVersions[address];
			if (outstandingAccessVersion == ORAMUtils.DUMMY_VERSION) {
				continue;
			}

			if (outstandingWriteVersion > currentWriteVersion ||
					(outstandingWriteVersion == currentWriteVersion && outstandingAccessVersion > currentAccessVersion)) {
				update(address, outstandingLocation, outstandingWriteVersion, outstandingAccessVersion,
						outstandingLocationUpdateVersion);
			} else if (outstandingWriteVersion == currentWriteVersion && outstandingAccessVersion == currentAccessVersion) {
				if (outstandingGraph.doesOverrides(outstandingLocationUpdateVersion, currentLocationUpdateVersion)) {
					update(address, outstandingLocation, outstandingWriteVersion, outstandingAccessVersion,
							outstandingLocationUpdateVersion);
				} else if (!outstandingGraph.doesOverrides(currentLocationUpdateVersion, outstandingLocationUpdateVersion)) {
					if (outstandingLocation > currentLocation ||
							(outstandingLocation == currentLocation && outstandingLocationUpdateVersion > currentLocationUpdateVersion)) {
						update(address, outstandingLocation, outstandingWriteVersion, outstandingAccessVersion,
								outstandingLocationUpdateVersion);
					}
				}
			}
		}
	}

	public void merge(int version, PathMap pathMap) {
		for (int address : pathMap.getStoredAddresses()) {
			/*int currentLocation = locations[address];
			int currentWriteVersion = writeVersions[address];
			int currentAccessVersion = accessVersions[address];
			int currentLocationUpdateVersion = locationUpdateVersions[address];*/
			int pathMapLocation = pathMap.getLocationOf(address);
			int pathMapWriteVersion = pathMap.getWriteVersionOf(address);
			int pathMapAccessVersion = pathMap.getAccessVersionOf(address);
			/*if (pathMapWriteVersion > currentWriteVersion ||
					(pathMapWriteVersion == currentWriteVersion && pathMapAccessVersion > currentAccessVersion)) {
				update(address, pathMapLocation, pathMapWriteVersion, pathMapAccessVersion,
						version);
			} else if (pathMapWriteVersion == currentWriteVersion && pathMapAccessVersion == currentAccessVersion) {

			}*/
			update(address, pathMapLocation, pathMapWriteVersion, pathMapAccessVersion,
					version);
		}
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

			ORAMUtils.serializeInteger(accessVersions[i], output, offset);
			offset += Integer.BYTES;

			ORAMUtils.serializeInteger(writeVersions[i], output, offset);
			offset += Integer.BYTES;

			ORAMUtils.serializeInteger(locationUpdateVersions[i], output, offset);
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
		accessVersions = new int[size];
		writeVersions = new int[size];
		for (int i = 0; i < size; i++) {
			locations[i] = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			accessVersions[i] = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			writeVersions[i] = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			locationUpdateVersions[i] = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;
		}

		return offset;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < locations.length; i++) {
			sb.append("A: ").append(i)
					.append(", L: ").append(locations[i])
					.append(", WV: ").append(writeVersions[i])
					.append(", AV: ").append(accessVersions[i])
					.append(", LUV: ").append(locationUpdateVersions[i]).append("\n");
		}
		return sb.toString();
	}

	public String toStringNonNull() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < locations.length; i++) {
			if (accessVersions[i] == ORAMUtils.DUMMY_VERSION) {
				continue;
			}
			sb.append("A: ").append(i)
					.append(", L: ").append(locations[i])
					.append(", WV: ").append(writeVersions[i])
					.append(", AV: ").append(accessVersions[i])
					.append(", LUV: ").append(locationUpdateVersions[i]).append("\n");
		}
		return sb.toString();
	}
}
