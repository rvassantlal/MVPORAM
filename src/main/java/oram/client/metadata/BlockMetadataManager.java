package oram.client.metadata;

import oram.client.structure.EvictionMap;
import oram.utils.ORAMUtils;

import java.util.*;

import static oram.utils.ORAMUtils.computeHashCode;

public class BlockMetadataManager {
	private final Map<Integer, EvictionMap> localEvictionMapHistory;
	private final Map<Integer, Integer> processedOutstandingVersions;
	private final Map<Integer, Map<Integer, BlockMetadata>> metadataHistory;
	private final OutstandingGraph outstandingGraph;
	private int blockOfInterest = -1;

	public BlockMetadataManager() {
		localEvictionMapHistory = new HashMap<>();
		processedOutstandingVersions = new HashMap<>();
		metadataHistory = new HashMap<>();
		outstandingGraph = new OutstandingGraph();
	}

	public void setBlockOfInterest(int blockOfInterest) {
		this.blockOfInterest = blockOfInterest;
	}

	public Map<Integer, EvictionMap> getLocalEvictionMapHistory() {
		return localEvictionMapHistory;
	}

	public Map<Integer, int[]> getLocalOutstandingVersionsHistory() {
		return outstandingGraph.getOutstandingVersions();
	}

	public void processMetadata(Map<Integer, EvictionMap> evictionMaps,
								Map<Integer, int[]> allOutstandingVersions,
								int newVersion,
								StringBuilder debugInfoBuilder) {
		localEvictionMapHistory.putAll(evictionMaps);
		outstandingGraph.addOutstandingVersions(allOutstandingVersions);
		Map<Integer, int[]> outstandingVersionsHistory = outstandingGraph.getOutstandingVersions();

		int[] receivedVersions = ORAMUtils.convertSetIntoOrderedArray(allOutstandingVersions.keySet());
		for (int receivedVersion : receivedVersions) {
			int[] outstandingVersions = allOutstandingVersions.get(receivedVersion);
			int outstandingVersionsHash = computeHashCode(outstandingVersions);
			debugInfoBuilder.append("Processing version ").append(receivedVersion).append("\n");
			debugInfoBuilder.append("\tOV: ").append(Arrays.toString(outstandingVersions)).append("\n");
			for (int outstandingVersion : outstandingVersions) {
				if (outstandingVersion == ORAMUtils.DUMMY_VERSION) {
					continue;
				}
				int[] ovOutstandingVersion = outstandingVersionsHistory.get(outstandingVersion);
				Set<Integer> concurrentVersions = findConcurrentVersions(receivedVersion, outstandingVersion, ovOutstandingVersion,
						outstandingVersionsHistory, debugInfoBuilder);
				debugInfoBuilder.append("\tConcurrent versions for OV ").append(outstandingVersion).append(": ").append(concurrentVersions).append("\n");
			}

			if (processedOutstandingVersions.containsKey(outstandingVersionsHash)) {
				int processedVersion = processedOutstandingVersions.get(outstandingVersionsHash);
				Map<Integer, BlockMetadata> metadata = metadataHistory.get(processedVersion);
				metadataHistory.put(receivedVersion, metadata);
				debugInfoBuilder.append("\tProcessed by version ").append(processedVersion).append("\n");
			} else {
				Map<Integer, BlockMetadata> mergedMetadata = mergeOutstandingMetadata(outstandingVersions,
						debugInfoBuilder);
				if (blockOfInterest != -1) {
					debugInfoBuilder.append("\tMerged outstanding metadata:\n");
					debugInfoBuilder.append("\t\t").append("Block ").append(blockOfInterest)
							.append(" -> ").append(mergedMetadata.get(blockOfInterest)).append("\n");
				}

				applyEvictionMaps(outstandingVersions, mergedMetadata, newVersion, debugInfoBuilder);
				if (blockOfInterest != -1) {
					debugInfoBuilder.append("\tUpdated metadata:\n");
					debugInfoBuilder.append("\t\t").append("Block ").append(blockOfInterest)
							.append(" -> ").append(mergedMetadata.get(blockOfInterest)).append("\n");
				} /*else {
					debugInfoBuilder.append("\tUpdated metadata:\n");
					mergedMetadata.keySet().stream().sorted().forEach(address -> {
						BlockMetadata blockMetadata = mergedMetadata.get(address);
						debugInfoBuilder.append("\t\t").append("Block ").append(address).append(" -> ").append(blockMetadata).append("\n");
					});
				}*/
				metadataHistory.put(receivedVersion, mergedMetadata);
				processedOutstandingVersions.put(outstandingVersionsHash, receivedVersion);
			}
		}
	}

	private Set<Integer> findConcurrentVersions(int startVersion, int ovVersion, int[] limitOutstandingVersions,
												Map<Integer, int[]> outstandingVersionsHistory, StringBuilder debugInfoBuilder) {
		Set<Integer> visitedOutstandingVersions = new HashSet<>();
		Queue<int[]> queue = new ArrayDeque<>();
		queue.add(outstandingVersionsHistory.get(startVersion));
		Set<Integer> limitOVSet = new HashSet<>(limitOutstandingVersions.length);
		int minLimitVersion = Integer.MAX_VALUE;
		for (int limitOutstandingVersion : limitOutstandingVersions) {
			limitOVSet.add(limitOutstandingVersion);
			if (limitOutstandingVersion < minLimitVersion) {
				minLimitVersion = limitOutstandingVersion;
			}
		}
		Set<Integer> concurrentVersions = new HashSet<>();
		while (!queue.isEmpty()) {
			int[] versions = queue.poll();
			for (int version : versions) {
				if (outstandingGraph.doesOverrides(version, limitOVSet)) {
					concurrentVersions.add(version);
				}
				if (version > minLimitVersion && version != ovVersion) {
					int[] newVersions = outstandingVersionsHistory.get(version);
					int hash = ORAMUtils.computeHashCode(newVersions);
					if (!visitedOutstandingVersions.contains(hash)) {
						queue.add(newVersions);
						visitedOutstandingVersions.add(hash);
					}
				}
			}
		}

		return concurrentVersions;
	}

	private void applyEvictionMaps(int[] outstandingVersions, Map<Integer, BlockMetadata> metadata,
								   int newVersion, StringBuilder debugInfoBuilder) {
		removeBlockLocationFromMetadata(outstandingVersions, metadata, newVersion, debugInfoBuilder);
		if (blockOfInterest != -1) {
			debugInfoBuilder.append("\tOutstanding metadata after removing:\n");
			debugInfoBuilder.append("\t\t").append("Block ").append(blockOfInterest)
					.append(" -> ").append(metadata.get(blockOfInterest)).append("\n");
		}
		addBlockLocationToMetadata(outstandingVersions, metadata, debugInfoBuilder);
		if (blockOfInterest != -1) {
			debugInfoBuilder.append("\tOutstanding metadata after adding:\n");
			debugInfoBuilder.append("\t\t").append("Block ").append(blockOfInterest)
					.append(" -> ").append(metadata.get(blockOfInterest)).append("\n");
		}
		/*for (int outstandingVersion : outstandingVersions) {
			if (outstandingVersion == ORAMUtils.DUMMY_VERSION) {
				continue;
			}
			EvictionMap evictionMap = localEvictionMapHistory.get(outstandingVersion);
			PartialTreeWithDuplicatedBlocks blocksRemovedFromPath = evictionMap.getBlocksRemovedFromPath();
			PartialTree blocksMovedToPath = evictionMap.getBlocksMovedToPath();
			if (blockOfInterest != -1) {
				debugInfoBuilder.append("\tEviction map ").append(outstandingVersion).append(":\n");
				debugInfoBuilder.append("\t\tBlock ").append(blockOfInterest).append(" removed from path:\n");
				debugInfoBuilder.append("\t\t\t L:").append(blocksRemovedFromPath.getLocations().get(blockOfInterest)).append("\n");
				debugInfoBuilder.append("\t\t\tCV:").append(blocksRemovedFromPath.getContentVersions().get(blockOfInterest)).append("\n");
				debugInfoBuilder.append("\t\t\tLV:").append(blocksRemovedFromPath.getLocationVersions().get(blockOfInterest)).append("\n");
				debugInfoBuilder.append("\t\tBlock ").append(blockOfInterest).append(" moved to path:\n");
				debugInfoBuilder.append("\t\t\t L:").append(blocksMovedToPath.getLocations().get(blockOfInterest)).append("\n");
				debugInfoBuilder.append("\t\t\tCV:").append(blocksMovedToPath.getContentVersions().get(blockOfInterest)).append("\n");
				debugInfoBuilder.append("\t\t\tLV:").append(blocksMovedToPath.getLocationVersions().get(blockOfInterest)).append("\n");
			}

			removeBlockLocationFromMetadata(outstandingVersion, metadata, blocksRemovedFromPath);
			addBlockLocationToMetadata(outstandingVersion, metadata, blocksMovedToPath);
		}*/
	}

	private void addBlockLocationToMetadata(int[] outstandingVersions, Map<Integer, BlockMetadata> metadata,
											StringBuilder debugInfoBuilder) {
		for (int outstandingVersion : outstandingVersions) {
			if (outstandingVersion == ORAMUtils.DUMMY_VERSION) {
				continue;
			}
			EvictionMap evictionMap = localEvictionMapHistory.get(outstandingVersion);
			PartialTree blocksMovedToPath = evictionMap.getBlocksMovedToPath();
			if (blockOfInterest != -1) {
				debugInfoBuilder.append("\tEviction map ").append(outstandingVersion).append(":\n");
				debugInfoBuilder.append("\t\tBlock ").append(blockOfInterest).append(" moved to path:\n");
				debugInfoBuilder.append("\t\t\t L:").append(blocksMovedToPath.getLocations().get(blockOfInterest)).append("\n");
				debugInfoBuilder.append("\t\t\tCV:").append(blocksMovedToPath.getContentVersions().get(blockOfInterest)).append("\n");
				debugInfoBuilder.append("\t\t\tLV:").append(blocksMovedToPath.getLocationVersions().get(blockOfInterest)).append("\n");
			}
			Map<Integer, Integer> emLocations = blocksMovedToPath.getLocations();
			Map<Integer, Integer> emContentVersions = blocksMovedToPath.getContentVersions();
			Map<Integer, Integer> emLocationVersions = blocksMovedToPath.getLocationVersions();
			for (Map.Entry<Integer, Integer> entry : emLocations.entrySet()) {
				int address = entry.getKey();
				int locationToAdd = entry.getValue();
				int contentVersion = emContentVersions.get(address);
				int locationVersion = emLocationVersions.get(address);

				BlockMetadata blockMetadata = metadata.get(address);
				if (blockMetadata == null) {
					blockMetadata = new BlockMetadata();
					blockMetadata.setNewValues(contentVersion, locationVersion, locationToAdd, outstandingVersion);
					metadata.put(address, blockMetadata);
					continue;
				}
				int mContentVersion = blockMetadata.getContentVersion();
				int mLocationVersion = blockMetadata.getLocationVersion();
				int mHighestLocation = blockMetadata.getHighestLocation();
				int mHighestLocationVersion = blockMetadata.getHighestLocationVersion();

				if (contentVersion > mContentVersion
						|| (contentVersion == mContentVersion && locationVersion > mLocationVersion)
						|| (contentVersion == mContentVersion && locationVersion == mLocationVersion
						&& locationToAdd > mHighestLocation)
						|| (contentVersion == mContentVersion && locationVersion == mLocationVersion
						&& locationToAdd == mHighestLocation && outstandingVersion > mHighestLocationVersion)
				) {
					blockMetadata.setNewValues(contentVersion, locationVersion, locationToAdd, outstandingVersion);
				}
			}
		}
	}

	private void removeBlockLocationFromMetadata(int[] outstandingVersions, Map<Integer, BlockMetadata> metadata,
												 int newVersion, StringBuilder debugInfoBuilder) {
		for (int outstandingVersion : outstandingVersions) {
			if (outstandingVersion == ORAMUtils.DUMMY_VERSION) {
				continue;
			}
			EvictionMap evictionMap = localEvictionMapHistory.get(outstandingVersion);
			PartialTreeWithDuplicatedBlocks blocksRemovedFromPath = evictionMap.getBlocksRemovedFromPath();
			if (blockOfInterest != -1) {
				debugInfoBuilder.append("\tEviction map ").append(outstandingVersion).append(":\n");
				debugInfoBuilder.append("\t\tBlock ").append(blockOfInterest).append(" removed from path:\n");
				debugInfoBuilder.append("\t\t\t L:").append(blocksRemovedFromPath.getLocations().get(blockOfInterest)).append("\n");
				debugInfoBuilder.append("\t\t\tCV:").append(blocksRemovedFromPath.getContentVersions().get(blockOfInterest)).append("\n");
				debugInfoBuilder.append("\t\t\tLV:").append(blocksRemovedFromPath.getLocationVersions().get(blockOfInterest)).append("\n");
			}
			Map<Integer, Set<Integer>> emLocations = blocksRemovedFromPath.getLocations();
			Map<Integer, Integer> emContentVersions = blocksRemovedFromPath.getContentVersions();
			Map<Integer, Integer> emLocationVersions = blocksRemovedFromPath.getLocationVersions();

			for (Map.Entry<Integer, Set<Integer>> entry : emLocations.entrySet()) {
				int address = entry.getKey();
				Set<Integer> locationsToRemove = entry.getValue();
				int contentVersion = emContentVersions.get(address);
				int locationVersion = emLocationVersions.get(address);
				BlockMetadata blockMetadata = metadata.get(address);
				if (blockMetadata == null) {
					blockMetadata = new BlockMetadata();
					blockMetadata.setNewValues(contentVersion, locationVersion, ORAMUtils.DUMMY_LOCATION, outstandingVersion);
					metadata.put(address, blockMetadata);
				} else {
					int mContentVersion = blockMetadata.getContentVersion();
					int mLocationVersion = blockMetadata.getLocationVersion();
					int mHighestLocation = blockMetadata.getHighestLocation();
					int mHighestLocationVersion = blockMetadata.getHighestLocationVersion();

					//the condition is true if one of the following conditions is satisfied:
					//1) there is a new block with new content version;
					//2) there is a new block with new location version;
					//3) remove the highest location. Have to confirm the dependency to not remove location allocated by a concurrent version.
					if (contentVersion > mContentVersion
							|| (contentVersion == mContentVersion && locationVersion > mLocationVersion)
							|| (contentVersion == mContentVersion && locationVersion == mLocationVersion
							&& locationsToRemove.contains(mHighestLocation)
							&& outstandingGraph.doesOverrides(outstandingVersion, mHighestLocationVersion))) {
						blockMetadata.setNewValues(contentVersion, locationVersion, ORAMUtils.DUMMY_LOCATION, outstandingVersion);
					}
				}
			}
		}
		for (Map.Entry<Integer, BlockMetadata> metadataEntry : metadata.entrySet()) {
			int address = metadataEntry.getKey();
			BlockMetadata blockMetadata = metadataEntry.getValue();
			int mContentVersion = blockMetadata.getContentVersion();
			int mLocationVersion = blockMetadata.getLocationVersion();
			int mHighestLocation = blockMetadata.getHighestLocation();
			int mHighestLocationVersion = blockMetadata.getHighestLocationVersion();
			for (int v = blockMetadata.getHighestLocationVersion(); v < newVersion; v++) {
				EvictionMap evictionMap = localEvictionMapHistory.get(v);
				if (evictionMap != null && outstandingGraph.doesOverrides(v, mHighestLocationVersion)
						&& outstandingGraph.doesOverrides(newVersion, v)) {
					PartialTreeWithDuplicatedBlocks blocksRemovedFromPath = evictionMap.getBlocksRemovedFromPath();
					Set<Integer> locationsToRemove = blocksRemovedFromPath.getLocations().get(address);
					if (locationsToRemove == null) {
						continue;
					}
					int contentVersion = blocksRemovedFromPath.getContentVersions().get(address);
					int locationVersion = blocksRemovedFromPath.getLocationVersions().get(address);
					if (contentVersion > mContentVersion
							|| (contentVersion == mContentVersion && locationVersion > mLocationVersion)
							|| (contentVersion == mContentVersion && locationVersion == mLocationVersion
							&& locationsToRemove.contains(mHighestLocation))) {
						blockMetadata.setNewValues(contentVersion, locationVersion, ORAMUtils.DUMMY_LOCATION, v);
						break;
					}
				}
			}
		}
	}

	private Map<Integer, BlockMetadata> mergeOutstandingMetadata(int[] outstandingVersions, StringBuilder debugInfoBuilder) {
		Map<Integer, BlockMetadata> mergedMetadata = new HashMap<>();
		for (int outstandingVersion : outstandingVersions) {
			if (outstandingVersion == ORAMUtils.DUMMY_VERSION) {
				continue;
			}
			Map<Integer, BlockMetadata> outstandingMetadata = metadataHistory.get(outstandingVersion);
			for (Map.Entry<Integer, BlockMetadata> entry : outstandingMetadata.entrySet()) {
				int address = entry.getKey();
				BlockMetadata outstandingBlockMetadata = entry.getValue();
				BlockMetadata mergedBlockMetadata = mergedMetadata.get(address);
				if (mergedBlockMetadata == null) {
					mergedMetadata.put(address, outstandingBlockMetadata.copy());
					continue;
				}
				int mContentVersion = mergedBlockMetadata.getContentVersion();
				int mLocationVersion = mergedBlockMetadata.getLocationVersion();
				int mHighestLocation = mergedBlockMetadata.getHighestLocation();
				int mHighestLocationVersion = mergedBlockMetadata.getHighestLocationVersion();
				int oContentVersion = outstandingBlockMetadata.getContentVersion();
				int oLocationVersion = outstandingBlockMetadata.getLocationVersion();
				int oHighestLocation = outstandingBlockMetadata.getHighestLocation();
				int oHighestLocationVersion = outstandingBlockMetadata.getHighestLocationVersion();

				//filtering block based on content and location versions
				if (mContentVersion > oContentVersion
						|| (mContentVersion == oContentVersion && mLocationVersion > oLocationVersion)) {
					//merged block metadata is newer block
					continue;
				} else if (oContentVersion > mContentVersion || oLocationVersion > mLocationVersion) {
					//outstanding block metadata is newer block
					mergedBlockMetadata.setNewValues(oContentVersion, oLocationVersion, oHighestLocation,
							oHighestLocationVersion);
					continue;
				}

				//here mContentVersion == oContentVersion && mLocationVersion == oLocationVersion
				//filtering block based on highest location and its version
				if (mHighestLocation == oHighestLocation && mHighestLocationVersion == oHighestLocationVersion) {
					//both metadata for this block are same
					continue;
				}

				//filtering based on version dependency
				boolean doesMergedOverridesOutstanding = outstandingGraph.doesOverrides(mHighestLocationVersion,
						oHighestLocationVersion);
				if (doesMergedOverridesOutstanding) {
					//merged block metadata is recent
					continue;
				}
				boolean doesOutstandingOverridesMerged = outstandingGraph.doesOverrides(oHighestLocationVersion,
						mHighestLocationVersion);
				if (doesOutstandingOverridesMerged) {
					//outstanding block metadata is recent
					mergedBlockMetadata.setNewValues(oContentVersion, oLocationVersion, oHighestLocation,
							oHighestLocationVersion);
					continue;
				}

				//mHighestLocationVersion and oHighestLocationVersion are concurrent
				if (oHighestLocation > mHighestLocation
						|| (oHighestLocation == mHighestLocation && oHighestLocationVersion > mHighestLocationVersion)) {
					mergedBlockMetadata.setNewValues(oContentVersion, oLocationVersion, oHighestLocation,
							oHighestLocationVersion);
				}

			}
		}
		return mergedMetadata;
	}


	public Map<Integer, BlockMetadata> getMetadata(int version) {
		return metadataHistory.get(version);
	}

	public boolean isInTree(int newVersion, int address) {
		Map<Integer, BlockMetadata> metadata = metadataHistory.get(newVersion);
		BlockMetadata blockMetadata = metadata.get(address);
		return blockMetadata.isInTree();
	}

	public boolean isInHighestLocation(int newVersion, int address, int location) {
		return metadataHistory.get(newVersion).get(address).isInHighestLocation(location);
	}
}
