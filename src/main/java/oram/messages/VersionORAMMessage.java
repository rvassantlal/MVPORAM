package oram.messages;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.LinkedList;
import java.util.List;

public class VersionORAMMessage extends ORAMMessage {
	private List<Double> versions;
	public VersionORAMMessage() {}

	public VersionORAMMessage(int oramId, List<Double> versions) {
		super(oramId);
		this.versions = versions;
	}

	public List<Double> getVersions() {
		return versions;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		super.writeExternal(out);
		out.writeInt(versions.size());
		for (double version : versions) {
			out.writeDouble(version);
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		super.readExternal(in);
		int size = in.readInt();
		versions = new LinkedList<>();
		while (size-- > 0) {
			versions.add(in.readDouble());
		}
	}
}
