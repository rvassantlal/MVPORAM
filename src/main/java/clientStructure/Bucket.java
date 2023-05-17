package clientStructure;

import java.io.*;
import java.util.Arrays;
import java.util.List;
//TODO: dummy fill the rest of the buckets
public class Bucket implements Externalizable{
	private Block[] blocks;
	public static final Integer MAX_SIZE=7;

	public Bucket(){
		blocks = new Block[MAX_SIZE];
	}

	
	public void writeBucket(List<Block> newBucketElements) {
		if (newBucketElements.size() <= MAX_SIZE){
			blocks= (Block[]) newBucketElements.toArray();
		}
	}

	public Block[] readBucket() {
		return this.blocks;
	}
	
	public String toString() {
		return Arrays.toString(blocks);
	}


	@Override
	public void writeExternal(ObjectOutput objectOutput) throws IOException {
		for (Block b : blocks){
			objectOutput.writeByte(b.getKey());
			objectOutput.write(b.getValue());
		}
	}

	@Override
	public void readExternal(ObjectInput objectInput) throws IOException {
		for (int i = 0; i < MAX_SIZE; i++) {
			byte key = objectInput.readByte();
			byte[] val = new byte[Block.standard_size];
			objectInput.read(val);
			blocks[i]=new Block(key,val);
		}
	}
}
