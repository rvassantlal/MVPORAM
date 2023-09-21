package oram.messages;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class GetPositionMap extends ORAMMessage {
	private int lastVersion;

	public GetPositionMap() {}

	public GetPositionMap(int oramId, int lastVersion) {
		super(oramId);
		this.lastVersion = lastVersion;
	}

	public int getLastVersion() {
		return lastVersion;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		super.writeExternal(out);
		out.writeInt(lastVersion);
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		super.readExternal(in);
		lastVersion = in.readInt();
	}
}
