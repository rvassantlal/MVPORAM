package oram.client.structure;

import oram.utils.ORAMUtils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class MergedPositionMap implements Externalizable {
	// This array maps a memory address to a pathId (max 256 paths).
	private int[] pathIds;
	private int[] versionIds;

	private int latestSequenceNumber;

	public MergedPositionMap() {
	}

	public MergedPositionMap(int[] versionIds, int[] pathIds) {
		this.versionIds = versionIds;
		this.pathIds = pathIds;
		this.latestSequenceNumber = ORAMUtils.DUMMY_VERSION;
	}

	public int getPathAt(int address) {
		return pathIds == null || pathIds.length < address ? ORAMUtils.DUMMY_PATH : pathIds[address];
	}

	public void setPathAt(int address, int pathId) {
		pathIds[address] = pathId;
	}

	public int[] getPathIds() {
		return pathIds;
	}

	public int getVersionIdAt(int address) {
		return versionIds == null || versionIds.length < address ? ORAMUtils.DUMMY_VERSION : versionIds[address];
	}

	public int[] getVersionIds() {
		return versionIds;
	}

	public void setVersionIdAt(int address, int newVersionId) {
		versionIds[address] = newVersionId;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(pathIds == null ? -1 : pathIds.length);
		for (int i = 0; i < pathIds.length; i++) {
			out.writeInt(pathIds[i]);
			out.writeInt(versionIds[i]);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException {
		int size = in.readInt();
		if (size != -1) {
			pathIds = new int[size];
			versionIds = new int[size];
			for (int i = 0; i < size; i++) {
				pathIds[i] = in.readInt();
				versionIds[i] = in.readInt();
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < pathIds.length; i++) {
			sb.append("( ").append(i).append(", ").append(pathIds[i]).append(", ").append(versionIds[i]).append(") ");
		}
		return sb.toString();
	}

	public void setLatestSequenceNumber(int newVersionId) {
		this.latestSequenceNumber = newVersionId;
	}

	public int getLatestSequenceNumber() {
		return latestSequenceNumber;
	}
}
