package oram.server.structure;

import oram.client.structure.EvictionMap;
import oram.utils.ORAMUtils;
import oram.utils.RawCustomExternalizable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class EncryptedDebugSnapshot implements RawCustomExternalizable {
	private int bucketSize;
	private BucketHolder[] tree;
	private Map<Integer, EncryptedStash> stashes;

	public EncryptedDebugSnapshot(int bucketSize) {
		this.bucketSize = bucketSize;
	}

	public EncryptedDebugSnapshot(BucketHolder[] tree, Map<Integer, EncryptedStash> stashes) {
		this.tree = tree;
		this.stashes = stashes;
	}

	public BucketHolder[] getTree() {
		return tree;
	}

	public Map<Integer, EncryptedStash> getStashes() {
		return stashes;
	}

	@Override
	public int getSerializedSize() {
		int size = 4;
		for (BucketHolder bucketHolder : tree) {
			size += 4;
			for (BucketSnapshot snapshot : bucketHolder.getOutstandingBucketsVersions()) {
				size += 4 + snapshot.getBucket().getSerializedSize();
			}
		}

		size += 4;
		for (EncryptedStash stash : stashes.values()) {
			size += 4 + stash.getSerializedSize();
		}

		return size;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = startOffset;

		byte[] treeLengthBytes = ORAMUtils.toBytes(tree.length);
		System.arraycopy(treeLengthBytes, 0, output, startOffset, 4);
		offset += 4;

		for (BucketHolder bucketHolder : tree) {
			byte[] sizeBytes = ORAMUtils.toBytes(bucketHolder.getOutstandingBucketsVersions().size());
			System.arraycopy(sizeBytes, 0, output, offset, 4);
			offset += 4;
			for (BucketSnapshot snapshot : bucketHolder.getOutstandingBucketsVersions()) {
				byte[] versionIdBytes = ORAMUtils.toBytes(snapshot.getVersionId());
				System.arraycopy(versionIdBytes, 0, output, offset, 4);
				offset += 4;

				offset = snapshot.getBucket().writeExternal(output, offset);
			}
		}

		byte[] nStashesBytes = ORAMUtils.toBytes(stashes.size());
		System.arraycopy(nStashesBytes, 0, output, offset, 4);
		offset += 4;

		int[] keys = new int[stashes.size()];
		int i = 0;
		for (int key : stashes.keySet()) {
			keys[i++] = key;
		}
		Arrays.sort(keys);
		for (int key : keys) {
			byte[] keyBytes = ORAMUtils.toBytes(key);
			System.arraycopy(keyBytes, 0, output, offset, 4);
			offset += 4;

			offset = stashes.get(key).writeExternal(output, offset);
		}

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = startOffset;

		byte[] treeLengthBytes = new byte[4];
		System.arraycopy(input, startOffset, treeLengthBytes, 0, 4);
		offset += 4;
		int treeLength = ORAMUtils.toNumber(treeLengthBytes);
		tree = new BucketHolder[treeLength];
		for (int i = 0; i < treeLength; i++) {
			byte[] sizeBytes = new byte[4];
			System.arraycopy(input, offset, sizeBytes, 0, 4);
			offset += 4;
			int size = ORAMUtils.toNumber(sizeBytes);

			ArrayList<BucketSnapshot> outstandingBucketsVersions = new ArrayList<>(size);
			for (int j = 0; j < size; j++) {
				byte[] versionIdBytes = new byte[4];
				System.arraycopy(input, offset, versionIdBytes, 0, 4);
				offset += 4;
				int versionId = ORAMUtils.toNumber(versionIdBytes);

				EncryptedBucket encryptedBucket = new EncryptedBucket(bucketSize);
				offset = encryptedBucket.readExternal(input, offset);
				outstandingBucketsVersions.add(new BucketSnapshot(versionId, encryptedBucket));
			}
			tree[i] = new BucketHolder(outstandingBucketsVersions);
		}

		byte[] nStashesBytes = new byte[4];
		System.arraycopy(input, offset, nStashesBytes, 0, 4);
		offset += 4;
		int nStashes = ORAMUtils.toNumber(nStashesBytes);
		stashes = new HashMap<>(nStashes);
		for (int i = 0; i < nStashes; i++) {
			byte[] keyBytes = new byte[4];
			System.arraycopy(input, offset, keyBytes, 0, 4);
			offset += 4;
			int key = ORAMUtils.toNumber(keyBytes);

			EncryptedStash stash = new EncryptedStash();
			offset = stash.readExternal(input, offset);
			stashes.put(key, stash);
		}

		return offset;
	}
}
