package oram.client.structure;

import oram.utils.CustomExternalizable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PositionMaps implements CustomExternalizable {
	private int newVersionId;
	private Map<Integer, PositionMap> positionMaps;

	public PositionMaps() {}

	public PositionMaps(int newVersionId, Map<Integer, PositionMap> positionMaps) {
		this.newVersionId = newVersionId;
		this.positionMaps = positionMaps;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(newVersionId);
		int[] keys = new int[positionMaps.size()];
		int k = 0;
		for (Integer i : positionMaps.keySet()) {
			keys[k++] = i;
		}
		Arrays.sort(keys);

		out.writeInt(positionMaps.size());
		for (int key : keys) {
			out.writeInt(key);
			positionMaps.get(key).writeExternal(out);
		}
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		newVersionId = in.readInt();
		int size = in.readInt();
		positionMaps = new HashMap<>(size);
		while (size-- > 0) {
			int key = in.readInt();
			PositionMap pm = new PositionMap();
			pm.readExternal(in);
			positionMaps.put(key, pm);
		}
	}

	public Map<Integer, PositionMap> getPositionMaps() {
		return positionMaps;
	}

	public int getNewVersionId() {
		return newVersionId;
	}
}
