package oram.server.structure;

import oram.utils.ORAMUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SecondaryORAMSnapshot extends AbstractORAMSnapshot {
	private final Map<Integer, EncryptedBucket> difTree;
	private final List<AbstractORAMSnapshot> previousVersions;

	public SecondaryORAMSnapshot(double versionId, ORAMContext oramContext) {
		super(versionId, oramContext);
		this.difTree = new HashMap<>();
		this.previousVersions = new LinkedList<>();
	}

	public List<AbstractORAMSnapshot> getPreviousVersions() {
		return previousVersions;
	}

	@Override
	public EncryptedPath getPath(byte pathId) {
		int treeHeight = oramContext.getTreeHeight();
		int[] pathLocations = ORAMUtils.computePathLocations(pathId, treeHeight);
		if (difTree.containsKey(pathLocations[0])) {
			int bucketSize = oramContext.getBucketSize();
			int blockSize = oramContext.getBlockSize();
			EncryptedBucket[] path = new EncryptedBucket[treeHeight + 1];
			for (int i = 0; i < pathLocations.length; i++) {
				path[i] = difTree.get(pathLocations[i]);
			}
			return new EncryptedPath(path, bucketSize, blockSize);
		}
		return null;
	}
}
