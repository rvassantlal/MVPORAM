package oram.messages;

import oram.utils.CustomExternalizable;

import java.io.*;

public class ORAMMessage implements CustomExternalizable {
	private int oramId;

	public ORAMMessage() {}

	public ORAMMessage(int oramId) {
		this.oramId = oramId;
	}

	public int getOramId() {
		return oramId;
	}

	@Override
	public void writeExternal(DataOutput out) throws IOException {
		out.writeInt(oramId);
	}

	@Override
	public void readExternal(DataInput in) throws IOException {
		oramId = in.readInt();
	}

	public int getSerializedSize() {
		return 4;
	}
}
