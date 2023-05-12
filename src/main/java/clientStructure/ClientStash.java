package clientStructure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

public class ClientStash implements Externalizable {
	public List<Block> contents;

	public ClientStash(){
		contents=new ArrayList<>();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		throw new UnsupportedOperationException();
	}
}
