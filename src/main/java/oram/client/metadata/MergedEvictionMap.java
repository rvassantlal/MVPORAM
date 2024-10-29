package oram.client.metadata;

public class MergedEvictionMap {
	private final PartialTreeWithDuplicatedBlocks blocksMovedToPath;
	private final PartialTreeWithDuplicatedBlocks blocksRemovedFromPath;

	public MergedEvictionMap(PartialTreeWithDuplicatedBlocks blocksMovedToPath,
							 PartialTreeWithDuplicatedBlocks blocksRemovedFromPath) {
		this.blocksMovedToPath = blocksMovedToPath;
		this.blocksRemovedFromPath = blocksRemovedFromPath;
	}

	public PartialTreeWithDuplicatedBlocks getBlocksMovedToPath() {
		return blocksMovedToPath;
	}

	public PartialTreeWithDuplicatedBlocks getBlocksRemovedFromPath() {
		return blocksRemovedFromPath;
	}
}
