package oram.testers;

import oram.client.structure.Block;
import oram.client.structure.Bucket;
import oram.client.structure.Stash;
import oram.utils.ORAMUtils;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.util.FastMath;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.bouncycastle.util.Arrays.reverse;

public class Playground {
	private static UniformIntegerDistribution uniformIntegerDistribution;
	private static SecureRandom rndGenerator;
	private static Set<Integer> slots;

	public static void main(String[] args) {
		rndGenerator = new SecureRandom();

		int height = 2;
		System.out.println("height: " + height);

		int levels = height + 1;
		int nPaths = 1 << height;
		System.out.println("#paths: " + nPaths);

		int bucketSize = 4;
		System.out.println("bucket size: " + bucketSize);

		int stashSize = bucketSize * levels;
		System.out.println("stash size: " + stashSize);

		int pathCapacity = bucketSize * (height + 1);
		System.out.println("path capacity: " + pathCapacity);
		uniformIntegerDistribution = new UniformIntegerDistribution(0, pathCapacity - 1);

		int k = bucketSize;
		System.out.println("k: " + k);
		slots = new HashSet<>(k);

		int nSlots = ORAMUtils.computeNumberOfSlots(height, bucketSize);
		for (int slot = 0; slot < nSlots; slot++) {
			int bucketId = (int)Math.floor((double) slot / bucketSize);
			int level = (int) FastMath.log(2, bucketId + 1);
			int slotIndex = slot % bucketSize;
			int reverseSlot = bucketId * bucketSize + slotIndex;
			System.out.printf("Slot %d -> bucket %d -> level %d " +
					"-> index %d -> %d\n", slot, bucketId, level, slotIndex, reverseSlot);
		}

		if (true) {
			return;
		}

		//Initialize path
		int pathId = rndGenerator.nextInt(nPaths);
		System.out.println("path id: " + pathId);

		int[] pathLocations = reverse(ORAMUtils.computePathLocations(pathId, height));
		System.out.println("\tlocations: " + Arrays.toString(pathLocations));

		Bucket[] path = new Bucket[levels];
		for (int i = 0; i < pathLocations.length; i++) {
			path[i] = new Bucket(bucketSize, 10, pathLocations[i]);
		}
		int address = 0;
		selectRandomSlots(slots, levels);
		for (int initialBlocksLocation : slots) {
			int level = initialBlocksLocation / bucketSize;
			int index = initialBlocksLocation % bucketSize;
			Bucket bucket = path[level];
			Block block = new Block(10, address, 1, "test".getBytes());
			address++;
			bucket.putBlock(index, block);
		}
		slots.clear();

		for (Bucket bucket : path) {
			System.out.println("\t" + bucket.getLocation() + " -> " + bucket);
		}

		int blockToAccess = rndGenerator.nextInt(address);
		int blockToAccessSlot = -1;

		//Initialize stash
		Stash stash = new Stash(10);
		for (int i = 0; i < stashSize; i++) {
			Block block = new Block(10, address, 1, "test".getBytes());
			address++;
			stash.putBlock(block);
		}
		System.out.println("stash (" + stash.size() + " blocks):");
		stash.getBlocks().keySet().stream().sorted().forEach(key -> System.out.println("\t" + stash.getBlock(key)));

		for (int i = 0; i < path.length; i++) {
			Bucket bucket = path[i];
			Block[] blocks = bucket.getBlocks();
			for (int j = 0; j < blocks.length; j++) {
				Block block = blocks[j];
				if (block == null) {
					continue;
				}
				if (block.getAddress() == blockToAccess) {
					blockToAccessSlot = i * bucketSize + j;
				}
			}
		}
		System.out.println("Block to access: " + blockToAccess);
		System.out.println("Block to access slot: " + blockToAccessSlot);

		Stash newStash = new Stash(10);
		Block[] selectedBlocksFromStash = selectBlocks(stash, k);
		System.out.println("Blocks to move to path:");
		for (Block blocksFromStash : selectedBlocksFromStash) {
			System.out.println("\t" + blocksFromStash);
		}
		slots.add(blockToAccessSlot);
		selectRandomSlots(slots, k);
		System.out.println("Slots to substitute:");
		int blockToMoveToPathIndex = 0;
		for (int slot : slots) {
			int level = slot / bucketSize;
			int index = slot % bucketSize;
			Bucket bucket = path[level];
			Block block = bucket.getBlock(index);
			System.out.println("\t" + slot + ": " + block);
			if (block != null) {
				newStash.putBlock(block);
			}
			bucket.putBlock(index, selectedBlocksFromStash[blockToMoveToPathIndex]);
			blockToMoveToPathIndex++;
		}
		slots.clear();
		newStash.getBlocks().putAll(stash.getBlocks());

		System.out.println("Substituted path:");
		for (Bucket bucket : path) {
			System.out.println("\t" + bucket.getLocation() + " -> " + bucket);
		}
		System.out.println("New stash (" + newStash.size() + " blocks):");
		newStash.getBlocks().keySet().stream().sorted().forEach(key -> System.out.println("\t" + newStash.getBlock(key)));
	}

	private static Block[] selectBlocks(Stash stash, int maxNBlocks) {
		int nBlocks = Math.min(stash.size(), maxNBlocks);
		int[] stashBlocksAddresses = ORAMUtils.convertSetIntoOrderedArray(stash.getBlocks().keySet());
		Set<Integer> selectedBlocksIndexes = new HashSet<>();
		while (selectedBlocksIndexes.size() < nBlocks) {
			selectedBlocksIndexes.add(rndGenerator.nextInt(stashBlocksAddresses.length));
		}
		Block[] blocks = new Block[nBlocks];
		int index = 0;
		for (int selectedBlockIndex : selectedBlocksIndexes) {
			int selectedBlockAddress = stashBlocksAddresses[selectedBlockIndex];
			blocks[index] = stash.getAndRemoveBlock(selectedBlockAddress);
			index++;
		}
		return blocks;
	}

	private static void selectRandomSlots(Set<Integer> slots, int nSlots) {
		while (slots.size() < nSlots) {
			slots.add(uniformIntegerDistribution.sample());
		}
	}
}
