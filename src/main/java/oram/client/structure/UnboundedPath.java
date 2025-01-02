package oram.client.structure;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class UnboundedPath {
	private final Map<Integer, LinkedList<Block>> path;

	public UnboundedPath(int[] bucketsLocations) {
		path = new HashMap<>(bucketsLocations.length);
		for (int bucketsLocation : bucketsLocations) {
			path.put(bucketsLocation, new LinkedList<>());
		}
	}

	public void putBlock(int bucket, Block block) {
		path.get(bucket).add(block);
	}
}
