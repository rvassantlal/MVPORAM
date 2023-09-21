package oram.messages;

import java.io.*;

public class StashPathORAMMessage extends ORAMMessage {

	private int pathId;

	public StashPathORAMMessage() {}

	public StashPathORAMMessage(int oramId, int pathId) {
		super(oramId);
		this.pathId = pathId;
	}

	public int getPathId() {
		return pathId;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		super.writeExternal(out);
		out.writeInt(pathId);
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		super.readExternal(in);
		pathId = in.readInt();
	}
}
