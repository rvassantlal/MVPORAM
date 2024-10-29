package oram.client.structure;

import oram.client.metadata.PartialTreeWithDuplicatedBlocks;
import oram.client.metadata.PartialTree;
import oram.utils.RawCustomExternalizable;

public class EvictionMap implements RawCustomExternalizable {
	private final PartialTree blocksMovedToPath;
	private final PartialTreeWithDuplicatedBlocks blocksRemovedFromPath;

	public EvictionMap() {
		this.blocksMovedToPath = new PartialTree();
		this.blocksRemovedFromPath = new PartialTreeWithDuplicatedBlocks();
	}

	public void reset() {
		blocksMovedToPath.reset();
		blocksRemovedFromPath.reset();
	}

	public void blockRemovedFromPath(Block block, int location) {
		blocksRemovedFromPath.put(block.getAddress(), location, block.getContentVersion(), block.getLocationVersion());
	}

	public void blockAddedBackToPath(Block block, int location) {
		blocksRemovedFromPath.remove(block.getAddress(), location, block.getContentVersion(), block.getLocationVersion());
	}


	public void moveBlockToPath(Block block, int location) {
		this.blocksMovedToPath.put(block, location);
	}

	public PartialTree getBlocksMovedToPath() {
		return blocksMovedToPath;
	}

	public PartialTreeWithDuplicatedBlocks getBlocksRemovedFromPath() {
		return blocksRemovedFromPath;
	}

	@Override
	public int getSerializedSize() {
		return blocksMovedToPath.getSerializedSize() + blocksRemovedFromPath.getSerializedSize();
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = blocksMovedToPath.writeExternal(output, startOffset);
		return blocksRemovedFromPath.writeExternal(output, offset);
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = blocksMovedToPath.readExternal(input, startOffset);
		return blocksRemovedFromPath.readExternal(input, offset);
	}
}
