package oram.client.structure;

import oram.utils.CustomExternalizable;
import oram.utils.ORAMUtils;

import java.io.*;
import java.util.Arrays;

public class PositionMap implements CustomExternalizable {
	private int[] locations;
	private int[] versionIds;
	private int[] address; //Used only with triple position map
	private int[] blockModificationVersions;

	public PositionMap() {
	}

	public PositionMap(int size) {
		this.versionIds = new int[size];
		this.locations = new int[size];
		this.blockModificationVersions = new int[size];
		Arrays.fill(locations, ORAMUtils.DUMMY_LOCATION);
	}

	public PositionMap(int[] versionIds, int[] locations) {
		this.versionIds = versionIds == null ? new int[0] : versionIds;
		this.locations = locations == null ? new int[0] : locations;
		this.address = null;
	}

	public PositionMap(int[] versionId, int[] pathId, int[] address, int[] blockModificationVersions) {
		this.versionIds = versionId;
		this.locations = pathId;
		this.address = address;
		this.blockModificationVersions = blockModificationVersions;
	}

	public int[] getAddress() {
		return address;
	}

	public int getPathAt(int address) {
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

	public void setPathAt(int address, int pathId) {
		if (address == ORAMUtils.DUMMY_ADDRESS) {
			return;
		}
		if (this.address != null) {
			if (this.address[0] == address) {
				locations[0] = pathId;
			} else if (this.address[1] == address) {
				locations[1] = pathId;
			}
		} else if (locations.length >= address) {
			locations[address] = pathId;
		}
	}

	public int[] getLocations() {
		return locations;
	}

	public int[] getVersionIds() {
		return versionIds;
	}

	public int getVersionIdAt(int address) {
		if (address == ORAMUtils.DUMMY_ADDRESS) {
			return ORAMUtils.DUMMY_VERSION;
		}
		if (this.address != null) {
			if (this.address[0] == address) {
				return versionIds[0];
			} else if (this.address[1] == address) {
				return versionIds[1];
			}
			return ORAMUtils.DUMMY_VERSION;
		} else if (versionIds.length < address) {
			return ORAMUtils.DUMMY_VERSION;
		}
		return versionIds[address];
	}

	public void setVersionIdAt(int address, int newVersionId) {
		if (address == ORAMUtils.DUMMY_ADDRESS) {
			return;
		}
		if (this.address != null) {
			if (this.address[0] == address) {
				versionIds[0] = newVersionId;
			} else if (this.address[1] == address) {
				versionIds[1] = newVersionId;
			}
		} else if (versionIds.length >= address) {
			versionIds[address] = newVersionId;
		}
	}

	public int getBlockModificationVersionAt(int address) {
		if (address == ORAMUtils.DUMMY_ADDRESS) {
			return ORAMUtils.DUMMY_VERSION;
		}
		if (this.address != null) {
			if (this.address[0] == address) {
				return blockModificationVersions[0];
			} else if (this.address[1] == address) {
				return blockModificationVersions[1];
			}
			return ORAMUtils.DUMMY_VERSION;
		} else if (blockModificationVersions.length < address) {
			return ORAMUtils.DUMMY_VERSION;
		}
		return blockModificationVersions[address];
	}

	public void setBlockModificationVersionAt(int address, int newVersionId) {
		if (address == ORAMUtils.DUMMY_ADDRESS) {
			return;
		}
		if (this.address != null) {
			if (this.address[0] == address) {
				blockModificationVersions[0] = newVersionId;
			} else if (this.address[1] == address) {
				blockModificationVersions[1] = newVersionId;
			}
		} else if (blockModificationVersions.length >= address) {
			blockModificationVersions[address] = newVersionId;
		}
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(locations.length);
		out.writeBoolean(address != null);
		for (int i = 0; i < locations.length; i++) {
			out.writeInt(locations[i]);
			out.writeInt(versionIds[i]);
			out.writeInt(blockModificationVersions[i]);
			if (address != null) {
				out.writeInt(address[i]);
			}
		}
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		int size = in.readInt();
		locations = new int[size];
		versionIds = new int[size];
		blockModificationVersions = new int[size];
		boolean isTriplePM = in.readBoolean();
		if (isTriplePM) {
			address = new int[size];
		}
		for (int i = 0; i < size; i++) {
			locations[i] = in.readInt();
			versionIds[i] = in.readInt();
			blockModificationVersions[i] = in.readInt();
			if (isTriplePM) {
				address[i] = in.readInt();
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (address != null) {
			sb.append(address[0]).append(", ").append(locations[0]).append(", ").append(blockModificationVersions[0])
					.append(", ").append(versionIds[0]).append("\n");
			sb.append(address[1]).append(", ").append(locations[1]).append(", ").append(blockModificationVersions[1])
					.append(", ").append(versionIds[1]).append("\n");
		} else {
			for (int i = 0; i < locations.length; i++) {
				sb.append(i).append(", ").append(locations[i]).append(", ").append(blockModificationVersions[i])
						.append(", ").append(versionIds[i]).append("\n");
			}
		}
		return sb.toString();
	}

	public String toStringNonNull() {
		StringBuilder sb = new StringBuilder();
		if (address != null) {
			sb.append(address[0]).append(", ").append(locations[0]).append(", ").append(versionIds[0]).append("\n");
			sb.append(address[1]).append(", ").append(locations[1]).append(", ").append(versionIds[1]).append("\n");
		} else {
			for (int i = 0; i < locations.length; i++) {
				if (locations[i] == ORAMUtils.DUMMY_LOCATION) {
					continue;
				}
				sb.append(i).append(", ").append(locations[i]).append(", ").append(blockModificationVersions[i])
						.append(", ").append(versionIds[i]).append("\n");
			}
		}
		return sb.toString();
	}
}
