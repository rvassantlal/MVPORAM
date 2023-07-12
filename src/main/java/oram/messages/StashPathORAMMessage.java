package oram.messages;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

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
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeInt(pathId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		pathId = in.readInt();
	}
}
