package oram.messages;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class StashPathORAMMessage extends ORAMMessage {

	private byte pathId;

	public StashPathORAMMessage() {}

	public StashPathORAMMessage(int oramId, byte pathId) {
		super(oramId);
		this.pathId = pathId;
	}

	public byte getPathId() {
		return pathId;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.write(pathId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		pathId = (byte) in.read();
	}
}
