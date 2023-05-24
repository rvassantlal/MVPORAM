package oram.messages;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class CreateORAMMessage extends ORAMMessage {
	private int treeHeight;

	public CreateORAMMessage() {}

	public CreateORAMMessage(int oramId, int treeHeight) {
		super(oramId);
		this.treeHeight = treeHeight;
	}

	public int getTreeHeight() {
		return treeHeight;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeInt(treeHeight);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		treeHeight = in.readInt();
	}
}
