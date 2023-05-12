package clientStructure;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class Bucket implements Serializable{
	private static final long serialVersionUID = -7859690712026222635L;
	private Block[] blocks;
	public static final Integer MAX_SIZE=7;

	public Bucket(){
		blocks = new Block[MAX_SIZE];
	}
	public Block[] readBucket() {
		return blocks;
	}
	
	public boolean writeBucket(List<Block> newBucketElements) {
		if (newBucketElements.size() <= MAX_SIZE){
			blocks= (Block[]) newBucketElements.toArray();
			return true;
		}
		return false;
	}
	
	public String toString() {
		return Arrays.toString(blocks);
	}
	
}
