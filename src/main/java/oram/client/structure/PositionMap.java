package oram.client.structure;

import oram.utils.ORAMUtils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class PositionMap implements Externalizable {
	// This array maps a memory address to a pathId (max 256 paths).
	private int pathId;
	private int versionId;

	private int address;

	public PositionMap() {
	}

	public PositionMap(int versionId, int pathId, int address) {
		this.versionId = versionId;
		this.pathId = pathId;
		this.address = address;
	}

	public int getPathAt(int address) {
		return address != this.address ? ORAMUtils.DUMMY_PATH : pathId;
	}

	public void setPathAt(int address, int pathId) {
		if (address == this.address)
			this.pathId = pathId;
	}

	public int getPathId() {
		return pathId;
	}

	public int getVersionIdAt(int address) {
		return address != this.address ? ORAMUtils.DUMMY_PATH : versionId;
	}

	public int getVersionId() {
		return versionId;
	}

	public void setVersionIdAt(int address, int newVersionId) {
		if (address == this.address)
			versionId = newVersionId;
	}

	public int getAddress() {
		return address;
	}
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(address);
		out.writeInt(pathId);
		out.writeInt(versionId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException {
		address = in.readInt();
		pathId = in.readInt();
		versionId = in.readInt();
	}


	/*
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < pathIds.length; i++) {
			sb.append("( ").append(i).append(", ").append(pathIds[i]).append(", ").append(versionIds[i]).append(") ");
		}
		return sb.toString();
	}*/
}
