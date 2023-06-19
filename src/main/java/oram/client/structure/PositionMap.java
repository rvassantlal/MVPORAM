package oram.client.structure;

import oram.utils.ORAMUtils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class PositionMap implements Externalizable {
	// This array maps a memory address to a pathId (max 256 paths).
	private byte[] pathIds;
	private double[] versionIds;

	private double versionId = -1;

	public PositionMap() {}

	public PositionMap(double[] versionIds, byte[] pathIds) {
		this.versionIds = versionIds;
		this.pathIds = pathIds;
	}

	public byte getPathAt(int address) {
		return pathIds == null || pathIds.length < address ? ORAMUtils.DUMMY_PATH : pathIds[address];
	}

	public void setPathAt(int address, byte pathId) {
		pathIds[address] = pathId;
	}

	public byte[] getPathIds() {
		return pathIds;
	}

	public double getVersionIdAt(int address) {
		return versionIds == null || versionIds.length < address ? ORAMUtils.DUMMY_VERSION : versionIds[address];
	}

	public double[] getVersionIds() {
		return versionIds;
	}

	public void setVersionIdAt(int address, double newVersionId) {
		versionIds[address] = newVersionId;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(pathIds == null ? -1 : pathIds.length);
		for (int i = 0; i < pathIds.length; i++) {
			out.write(pathIds[i]);
			out.writeDouble(versionIds[i]);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException {
		int size = in.readInt();
		if (size != -1) {
			pathIds = new byte[size];
			versionIds = new double[size];
			for (int i = 0; i < size; i++) {
				pathIds[i] = (byte) in.read();
				versionIds[i] = in.readDouble();
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

    public void setVersionId(double versionId) {
		this.versionId = versionId;
    }

	public double getVersionId() {
		return versionId;
	}
}
