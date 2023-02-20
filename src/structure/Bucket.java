package structure;

import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;

public class Bucket implements Serializable{
	private static final long serialVersionUID = -7859690712026222635L;
	private TreeMap<Short, Short> blocks = new TreeMap<>();
	public static final Integer MAX_SIZE=4;
	
	public Map<Short, Short> readBucket() {
		return blocks.descendingMap();
	}
	
	public void writeBucket(Map<Short,Short> newBucketElements) {
		blocks.clear();
		if (newBucketElements.size() <= MAX_SIZE)
			blocks.putAll(newBucketElements);
	}
	
}
