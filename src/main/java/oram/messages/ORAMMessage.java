package oram.messages;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class ORAMMessage implements Externalizable {
	private int oramId;

	public ORAMMessage() {}

	public ORAMMessage(int oramId) {
		this.oramId = oramId;
	}

	public int getOramId() {
		return oramId;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(oramId);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		oramId = in.readInt();
	}
}
