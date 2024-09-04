package oram.client.structure;

import java.util.ArrayList;
import java.util.LinkedList;

public class UnboundedPath {
	private ArrayList<LinkedList<Block>> buckets;

	public UnboundedPath(int nLevels) {
		this.buckets = new ArrayList<>(nLevels);
		for (int i = 0; i < nLevels; i++) {
			this.buckets.add(new LinkedList<>());
		}
	}

	public void addBlock(int level, Block block) {
		this.buckets.get(level).add(block);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < this.buckets.size(); i++) {
			sb.append("Level ").append(i).append(": ");
			for (Block block : this.buckets.get(i)) {
				sb.append(block).append(" ");
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	public String toStringSize() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < this.buckets.size(); i++) {
			sb.append("Level ").append(i).append(": ").append(buckets.get(i).size()).append(" blocks\n");
		}
		return sb.toString();
	}
}
