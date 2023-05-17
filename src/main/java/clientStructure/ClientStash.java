package clientStructure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClientStash implements Externalizable {
	private List<Block> contents;
	private int size;

	public ClientStash(){
		contents=new ArrayList<>();
	}

	public ClientStash(int size){
		contents=new ArrayList<>();
		this.size=size;
	}

	public ClientStash(List<Block> contents){
		this.contents=contents;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		for (Block b : contents){
			out.writeByte(b.getKey());
			out.write(b.getValue());
		}
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		for (int i = 0; i < size; i++) {
			byte key = in.readByte();
			byte[] val = new byte[Block.standard_size];
			in.read(val);
			contents.add(new Block(key,val));
		}
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

	public int size() {
		return contents.size();
	}
}
