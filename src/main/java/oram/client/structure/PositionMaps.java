package oram.client.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class PositionMaps implements Externalizable {
	private int newVersionId;
	private int[] outstandingVersionIds;
	private PositionMap[] positionMaps;

	public PositionMaps() {
	}

	public PositionMaps(int newVersionId, int[] outstandingVersionIds,
						PositionMap[] positionMaps) {
		this.newVersionId = newVersionId;
		this.outstandingVersionIds = outstandingVersionIds;
		this.positionMaps = positionMaps;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(newVersionId);
		out.writeInt(outstandingVersionIds.length);
		for (int outstandingVersionId : outstandingVersionIds) {
			out.writeInt(outstandingVersionId);
		}
		for (PositionMap positionMap : positionMaps) {
			positionMap.writeExternal(out);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException {
		newVersionId = in.readInt();
		int size = in.readInt();
		outstandingVersionIds = new int[size];
		positionMaps = new PositionMap[size];
		for (int i = 0; i < size; i++) {
			outstandingVersionIds[i] = in.readInt();
		}
		for (int i = 0; i < size; i++) {
			PositionMap e = new PositionMap();
			e.readExternal(in);
			positionMaps[i] = e;
		}
	}

	public int[] getOutstandingVersionIds() {
		return outstandingVersionIds;
	}

	public PositionMap[] getPositionMaps() {
		return positionMaps;
	}

	public int getNewVersionId() {
		return newVersionId;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("PositionMaps{ ");
		for (int i = 0; i < positionMaps.length; i++) {
			sb.append(outstandingVersionIds[i]).append(" : [").append(positionMaps[i].toString()).append("] ");
		}
		sb.append("} ");
		return sb.toString();

	}
}
