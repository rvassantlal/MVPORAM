package clientStructure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClientStash implements Externalizable {
	public List<Block> contents;

	public ClientStash(){
		contents=new ArrayList<>();
	}

	public ClientStash(List<Block> contents){
		this.contents=contents;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		throw new UnsupportedOperationException();
	}

	public List<Block> getBlocks() {
		return contents;
	}

	public void remove(Block block) {

		contents.remove(block);
	}

	public void putBlock(Block b) {
		contents.add(b);
	}

	public byte[] getBlock(byte key){
		Optional<Block> block = contents.stream().filter(block1 -> block1.getKey() == key).findFirst();
		return block.map(Block::getValue).orElse(null);
	}
}
