package oram.client.structure;

import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.Arrays;

public class PositionMap implements RawCustomExternalizable {
	private int[] locations;
	private int[] locationVersions;
	private int[] address; //Used only with triple position map
	private int[] contentVersions;

	public PositionMap() {
	}

	public PositionMap(int size) {
		this.locationVersions = new int[size];
		this.locations = new int[size];
		this.contentVersions = new int[size];
		Arrays.fill(locations, ORAMUtils.DUMMY_LOCATION);
		Arrays.fill(locationVersions, ORAMUtils.DUMMY_VERSION);
		Arrays.fill(contentVersions, ORAMUtils.DUMMY_VERSION);
	}

	public PositionMap(int[] locations, int[] locationVersions) {
		this.locationVersions = locationVersions == null ? new int[0] : locationVersions;
		this.locations = locations == null ? new int[0] : locations;
		this.address = null;
	}

	public PositionMap(int[] address, int[] locations, int[] locationVersions, int[] contentVersions) {
		this.locationVersions = locationVersions;
		this.locations = locations;
		this.address = address;
		this.contentVersions = contentVersions;
	}

	public int[] getAddress() {
		return address;
	}

	public int getLocationOf(int address) {
		if (address == ORAMUtils.DUMMY_ADDRESS) {
			return ORAMUtils.DUMMY_LOCATION;
		}
		if (this.address != null) {
			if (this.address[0] == address) {
				return locations[0];
			} else if (this.address[1] == address) {
				return locations[1];
			}
			return ORAMUtils.DUMMY_LOCATION;
		} else if (locations.length < address) {
			return ORAMUtils.DUMMY_LOCATION;
		}
		return locations[address];
	}

	public void setLocationOf(int address, int location) {
		if (address == ORAMUtils.DUMMY_ADDRESS) {
			return;
		}
		if (this.address != null) {
			if (this.address[0] == address) {
				locations[0] = location;
			} else if (this.address[1] == address) {
				locations[1] = location;
			}
		} else if (locations.length >= address) {
			locations[address] = location;
		}
	}

	public int[] getLocations() {
		return locations;
	}

	public int[] getLocationVersions() {
		return locationVersions;
	}

	public int getLocationVersionOf(int address) {
		if (address == ORAMUtils.DUMMY_ADDRESS) {
			return ORAMUtils.DUMMY_VERSION;
		}
		if (this.address != null) {
			if (this.address[0] == address) {
				return locationVersions[0];
			} else if (this.address[1] == address) {
				return locationVersions[1];
			}
			return ORAMUtils.DUMMY_VERSION;
		} else if (locationVersions.length < address) {
			return ORAMUtils.DUMMY_VERSION;
		}
		return locationVersions[address];
	}

	public void setLocationVersionOf(int address, int newLocationVersion) {
		if (address == ORAMUtils.DUMMY_ADDRESS) {
			return;
		}
		if (this.address != null) {
			if (this.address[0] == address) {
				locationVersions[0] = newLocationVersion;
			} else if (this.address[1] == address) {
				locationVersions[1] = newLocationVersion;
			}
		} else if (locationVersions.length >= address) {
			locationVersions[address] = newLocationVersion;
		}
	}

	public int getContentVersionOf(int address) {
		if (address == ORAMUtils.DUMMY_ADDRESS) {
			return ORAMUtils.DUMMY_VERSION;
		}
		if (this.address != null) {
			if (this.address[0] == address) {
				return contentVersions[0];
			} else if (this.address[1] == address) {
				return contentVersions[1];
			}
			return ORAMUtils.DUMMY_VERSION;
		} else if (contentVersions.length < address) {
			return ORAMUtils.DUMMY_VERSION;
		}
		return contentVersions[address];
	}

	public void setContentVersionOf(int address, int newContentVersion) {
		if (address == ORAMUtils.DUMMY_ADDRESS) {
			return;
		}
		if (this.address != null) {
			if (this.address[0] == address) {
				contentVersions[0] = newContentVersion;
			} else if (this.address[1] == address) {
				contentVersions[1] = newContentVersion;
			}
		} else if (contentVersions.length >= address) {
			contentVersions[address] = newContentVersion;
		}
	}

	@Override
	public int getSerializedSize() {
		return 4 + 1 + locations.length * (4 * 3) + (address != null ? locations.length * 4 : 0);
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;
		byte[] sizeBytes = ORAMUtils.toBytes(locations.length);
		System.arraycopy(sizeBytes, 0, output, offset, 4);
		offset += 4;

		output[offset] = (byte) (address != null ? 1 : 0);
		offset++;

		for (int i = 0; i < locations.length; i++) {
			byte[] locationBytes = ORAMUtils.toBytes(locations[i]);
			System.arraycopy(locationBytes, 0, output, offset, 4);
			offset += 4;

			byte[] versionIdBytes = ORAMUtils.toBytes(locationVersions[i]);
			System.arraycopy(versionIdBytes, 0, output, offset, 4);
			offset += 4;

			byte[] blockModificationVersionBytes = ORAMUtils.toBytes(contentVersions[i]);
			System.arraycopy(blockModificationVersionBytes, 0, output, offset, 4);
			offset += 4;

			if (address != null) {
				byte[] addressBytes = ORAMUtils.toBytes(address[i]);
				System.arraycopy(addressBytes, 0, output, offset, 4);
				offset += 4;
			}
		}

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;

		byte[] sizeBytes = new byte[4];
		System.arraycopy(input, offset, sizeBytes, 0, 4);
		offset += 4;
		int size = ORAMUtils.toNumber(sizeBytes);

		boolean isTriplePM = input[offset] == 1;
		offset++;

		locations = new int[size];
		locationVersions = new int[size];
		contentVersions = new int[size];
		if (isTriplePM) {
			address = new int[size];
		}
		for (int i = 0; i < size; i++) {
			byte[] locationBytes = new byte[4];
			System.arraycopy(input, offset, locationBytes, 0, 4);
			offset += 4;
			locations[i] = ORAMUtils.toNumber(locationBytes);

			byte[] versionIdBytes = new byte[4];
			System.arraycopy(input, offset, versionIdBytes, 0, 4);
			offset += 4;
			locationVersions[i] = ORAMUtils.toNumber(versionIdBytes);

			byte[] blockModificationVersionBytes = new byte[4];
			System.arraycopy(input, offset, blockModificationVersionBytes, 0, 4);
			offset += 4;
			contentVersions[i] = ORAMUtils.toNumber(blockModificationVersionBytes);

			if (isTriplePM) {
				byte[] addressBytes = new byte[4];
				System.arraycopy(input, offset, addressBytes, 0, 4);
				offset += 4;
				address[i] = ORAMUtils.toNumber(addressBytes);
			}
		}

		return offset;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (address != null) {
			sb.append(address[0]).append(", ").append(locations[0]).append(", ").append(contentVersions[0])
					.append(", ").append(locationVersions[0]).append("\n");
			sb.append(address[1]).append(", ").append(locations[1]).append(", ").append(contentVersions[1])
					.append(", ").append(locationVersions[1]).append("\n");
		} else {
			for (int i = 0; i < locations.length; i++) {
				sb.append(i).append(", ").append(locations[i]).append(", ").append(contentVersions[i])
						.append(", ").append(locationVersions[i]).append("\n");
			}
		}
		return sb.toString();
	}

	public String toStringNonNull() {
		StringBuilder sb = new StringBuilder();
		if (address != null) {
			sb.append(address[0]).append(", ").append(locations[0]).append(", ").append(locationVersions[0]).append("\n");
			sb.append(address[1]).append(", ").append(locations[1]).append(", ").append(locationVersions[1]).append("\n");
		} else {
			for (int i = 0; i < locations.length; i++) {
				if (locations[i] == ORAMUtils.DUMMY_LOCATION) {
					continue;
				}
				sb.append(i).append(", ").append(locations[i]).append(", ").append(contentVersions[i])
						.append(", ").append(locationVersions[i]).append("\n");
			}
		}
		return sb.toString();
	}
}
