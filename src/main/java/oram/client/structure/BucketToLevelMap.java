package oram.client.structure;

import oram.utils.ORAMUtils;

import java.util.Arrays;
import java.util.NavigableMap;
import java.util.TreeMap;

public class BucketToLevelMap {
	private final NavigableMap<Integer, Integer> bucketsToLevel;

	public BucketToLevelMap(int height) {
		this.bucketsToLevel = new TreeMap<>();
		int accumulator = 0;
		bucketsToLevel.put(0, 0);
		for (int i = 0; i < height; i++) {
			accumulator += (1 << i);
			bucketsToLevel.put(accumulator, i + 1);
		}
	}

	public int toLevel(int bucket) {
		return bucketsToLevel.floorEntry(bucket).getValue();
	}
}
