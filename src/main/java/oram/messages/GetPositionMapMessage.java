package oram.messages;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class GetPositionMapMessage extends ORAMMessage {
	private int lastVersion;
	public GetPositionMapMessage() {}

	public GetPositionMapMessage(int oramId, int lastVersion) {
		super(oramId);
		this.lastVersion = lastVersion;
	}

	public int getLastVersion() {
		return lastVersion;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeInt(lastVersion);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		lastVersion = in.readInt();
	}



}
