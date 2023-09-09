package oram.client.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;

public class PositionMaps implements Externalizable {
	private int newVersionId;
	private int[] outstandingVersionIds;
	private Map<Integer,PositionMap> positionMaps;

	public PositionMaps() {
	}

	public PositionMaps(int newVersionId, int[] outstandingVersionIds,
						Map<Integer,PositionMap> positionMaps) {
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
		for (Map.Entry<Integer, PositionMap> positionMapEntry : positionMaps.entrySet()) {
			out.writeInt(positionMapEntry.getKey());
			positionMapEntry.getValue().writeExternal(out);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException {
		newVersionId = in.readInt();
		int size = in.readInt();
		outstandingVersionIds = new int[size];
		positionMaps = new HashMap<>(size);
		for (int i = 0; i < size; i++) {
			outstandingVersionIds[i] = in.readInt();
		}
		for (int i = 0; i < size; i++) {
			int position = in.readInt();
			PositionMap e = new PositionMap();
			e.readExternal(in);
			positionMaps.put(position,e);
		}
	}

	public int[] getOutstandingVersionIds() {
		return outstandingVersionIds;
	}

	public Map<Integer,PositionMap> getPositionMaps() {
		return positionMaps;
	}

	public int getNewVersionId() {
		return newVersionId;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("PositionMaps{ ");
		/*for (int i = 0; i < positionMaps.length; i++) {
			sb.append(outstandingVersionIds[i]).append(" : [").append(positionMaps[i].toString()).append("] ");
		}*/ //TODO:fix
		sb.append("} ");
		return sb.toString();

	}
}
