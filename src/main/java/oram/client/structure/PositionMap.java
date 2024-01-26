package oram.client.structure;

import oram.utils.CustomExternalizable;
import oram.utils.ORAMUtils;

import java.io.*;
import java.util.Arrays;

public class PositionMap implements CustomExternalizable {
	private int[] pathIds;
	private int[] versionIds;
	private int address; //Used only with triple position map

	public PositionMap() {
	}

	public PositionMap(int size) {
		this.versionIds = new int[size];
		this.pathIds = new int[size];
		Arrays.fill(pathIds, ORAMUtils.DUMMY_PATH);
	}

	public PositionMap(int[] versionIds, int[] pathIds) {
		this.versionIds = versionIds == null ? new int[0] : versionIds;
		this.pathIds = pathIds == null ? new int[0] : pathIds;
		this.address = -1;
	}

	public PositionMap(int versionId, int pathId, int address) {
		this.versionIds = new int[] {versionId};
		this.pathIds = new int[] {pathId};
		this.address = address;
	}

	public int getAddress() {
		return address;
	}

	public int getPathAt(int address) {
		if (pathIds.length == 1 && this.address == address) {
			return pathIds[0];
		} else if (pathIds.length < address) {
			return ORAMUtils.DUMMY_PATH;
		}
		return pathIds[address];
	}

	public void setPathAt(int address, int pathId) {
		if (pathIds.length == 1 && this.address == address) {
			pathIds[0] = pathId;
		} else if (pathIds.length >= address) {
			pathIds[address] = pathId;
		}
	}

	public int[] getPathIds() {
		return pathIds;
	}

	public int getVersionIdAt(int address) {
		if (versionIds.length == 1 && this.address == address) {
			return versionIds[0];
		} else if (versionIds.length < address) {
			return ORAMUtils.DUMMY_VERSION;
		}
		return versionIds[address];
	}

	public void setVersionIdAt(int address, int newVersionId) {
		if (versionIds.length == 1 && this.address == address) {
			versionIds[0] = newVersionId;
		} else if (versionIds.length >= address) {
			versionIds[address] = newVersionId;
		}
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(pathIds.length);
		for (int i = 0; i < pathIds.length; i++) {
			out.writeInt(pathIds[i]);
			out.writeInt(versionIds[i]);
		}
		if (pathIds.length == 1) {
			out.writeInt(address);
		}
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		int size = in.readInt();
		pathIds = new int[size];
		versionIds = new int[size];
		for (int i = 0; i < size; i++) {
			pathIds[i] = in.readInt();
			versionIds[i] = in.readInt();
		}
		if (size == 1) {
			address = in.readInt();
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (versionIds.length == 1) {
			sb.append(address).append(", ").append(pathIds[0]).append(", ").append(versionIds[0]).append("\n");
		} else {
			for (int i = 0; i < pathIds.length; i++) {
				sb.append(i).append(", ").append(pathIds[i]).append(", ").append(versionIds[i]).append("\n");
			}
		}
		return sb.toString();
	}
}
