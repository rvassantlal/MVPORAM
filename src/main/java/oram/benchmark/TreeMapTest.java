package oram.benchmark;

import oram.client.structure.Bucket;
import oram.client.structure.PathMap;
import oram.client.structure.PositionMap;
import oram.utils.ORAMUtils;

import java.util.*;

public class TreeMapTest {
	public static void main(String[] args) {
		int treeHeight = 2;
		int bucketSize = 1;
		int blockSize = 16;
		int nConcurrentClients = 1;
		int nAccesses = 10;
		int treeSize = ORAMUtils.computeNumberOfNodes(treeHeight);
		int treeCapacity = ORAMUtils.computeTreeSize(treeHeight, bucketSize);

		System.out.println("Tree height: " + treeHeight);
		System.out.println("Bucket size: " + bucketSize);
		System.out.println("Block size: " + blockSize);
		System.out.println("Tree capacity: " + treeCapacity);

		ArrayList<Set<Bucket>> tree = new ArrayList<>(treeCapacity);
		for (int i = 0; i < treeSize; i++) {
			Bucket bucket = new Bucket(bucketSize, blockSize, i);
			HashSet<Bucket> node = new HashSet<>();
			node.add(bucket);
			tree.add(node);
		}

		int versionGenerator = 1;
		Map<Integer, PathMap> pathMapHistory = new HashMap<>();
		ClientContext[] clients = new ClientContext[nConcurrentClients];
		for (int i = 0; i < nConcurrentClients; i++) {
			clients[i] = new ClientContext(treeCapacity);
		}

		Set<Integer> outstandingVersions = new HashSet<>();
		Map<Integer, Set<Integer>> outstandingVersionsHistory = new HashMap<>();

		for (int i = 0; i < nAccesses; i++) {
			Set<Integer> currentOutstandingVersions = new HashSet<>(outstandingVersions);
			for (ClientContext client : clients) {
				int version = versionGenerator++;
				client.access(version, pathMapHistory, tree);
			}

		}
	}

	private static class ClientContext {
		private final int treeCapacity;
		private final PositionMap positionMap;

		public ClientContext(int treeCapacity) {
			this.treeCapacity = treeCapacity;
			positionMap = new PositionMap(treeCapacity);
		}

		public void access(int version, Map<Integer, PathMap> pathMapHistory, ArrayList<Set<Bucket>> tree) {
			mergePathMaps(pathMapHistory);
		}

		private void mergePathMaps(Map<Integer, PathMap> pathMapHistory) {

		}
	}
}
