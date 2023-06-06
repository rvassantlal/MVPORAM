package oram.server.structure;

import oram.utils.ORAMUtils;

public class MainORAMSnapshot extends AbstractORAMSnapshot {
	private final EncryptedBucket[] tree;

	public MainORAMSnapshot(double versionId, ORAMContext oramContext) {
		super(versionId, oramContext);
		this.tree = new EncryptedBucket[oramContext.getTreeSize()];
	}

	@Override
	public EncryptedPath getPath(byte pathId) {
		int treeHeight = oramContext.getTreeHeight();
		int bucketSize = oramContext.getBucketSize();
		int blockSize = oramContext.getBlockSize();
		int[] pathLocations = ORAMUtils.computePathLocations(pathId, treeHeight);
		EncryptedBucket[] path = new EncryptedBucket[treeHeight + 1];
		for (int i = 0; i < pathLocations.length; i++) {
			path[i] = tree[pathLocations[i]];
		}
		return new EncryptedPath(path, bucketSize, blockSize);
	}
}
