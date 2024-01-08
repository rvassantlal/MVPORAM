package oram.utils;

import oram.messages.ORAMMessage;
import oram.server.structure.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

public class ORAMUtils {
	public static final int DUMMY_VERSION = 0;
	public static final int DUMMY_PATH = -1;
	public static final int DUMMY_ADDRESS = -1;
	public static final byte[] DUMMY_BLOCK = new byte[0];

	public static int computeTreeSize(int treeHeight, int bucketSize) {
		return computeNumberOfNodes(treeHeight) * bucketSize;
	}

	public static int computeNumberOfNodes(int treeHeight) {
		return (1 << treeHeight + 1) - 1;
	}

	public static int[] computePathLocations(int pathId, int treeHeight) {
		int offset = pathId;
		int[] locations = new int[treeHeight + 1];
		for (int height = treeHeight; height >= 0; height--) {
			int level = (1 << height) - 1;
			if (height < treeHeight)
				offset = offset / 2;
			locations[treeHeight - height] = level + offset;
		}
		return locations;
	}

	public static List<Integer> computePathLocationsList(int pathId, int treeHeight) {
		int offset = pathId;
		List<Integer> locations = new ArrayList<>(treeHeight+1);
		for (int height = treeHeight; height >= 0; height--) {
			int level = (1 << height) - 1;
			if (height < treeHeight)
				offset = offset / 2;
			locations.add(treeHeight - height, level + offset);
		}
		return locations;
	}

	public static List<Integer> computePathIntersection(int treeLevels, int[] pathLocationsA, int[] pathLocationsB) {
		List<Integer> commonNodes = new ArrayList<>();
		for (int levels = treeLevels - 1; levels >= 0; levels--) {
			if (pathLocationsA[levels] == pathLocationsB[levels])
				commonNodes.add(pathLocationsA[levels]);
			else
				break;
		}

		Collections.reverse(commonNodes);
		return commonNodes;
	}

	public static byte[] serializeRequest(ServerOperationType operation, ORAMMessage request) {
		int dataSize = 1 + request.getSerializedSize();
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream(dataSize);
			 DataOutputStream out = new DataOutputStream(bos)) {
			out.writeByte(operation.ordinal());
			request.writeExternal(out);

			out.flush();
			bos.flush();
			return bos.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}

	public static int toNumber(byte[] numberInBytes) {
		int number = Byte.toUnsignedInt(numberInBytes[0]);
		number <<= 8;
		number |= Byte.toUnsignedInt(numberInBytes[1]);
		number <<= 8;
		number |= Byte.toUnsignedInt(numberInBytes[2]);
		number <<= 8;
		number |= Byte.toUnsignedInt(numberInBytes[3]);
		return number;
	}

	public static byte[] toBytes(int number) {
		byte[] result = new byte[4];
		result[3] = (byte) number;
		number >>= 8;
		result[2] = (byte) number;
		number >>= 8;
		result[1] = (byte) number;
		number >>= 8;
		result[0] = (byte) number;

		return result;
	}

	public static int computePathLength(int treeHeight, int bucketSize) {
		return (treeHeight + 1) * bucketSize;
	}

	public static int computePathSize(int treeHeight, int bucketSize, int blockSize) {
		return (treeHeight + 1) * bucketSize * blockSize;
	}

	public static long computeDatabaseSize(int treeHeight, int bucketSize, int blockSize) {
		return (long) computeTreeSize(treeHeight, bucketSize) * blockSize;
	}

	public static byte[] serializeEncryptedPathAndStash(EncryptedStashesAndPaths encryptedStashesAndPaths) {
		int dataSize = encryptedStashesAndPaths.getSerializedSize();
		byte[] result = new byte[dataSize];
		EncryptedStash[] encryptedStashes = encryptedStashesAndPaths.getEncryptedStashes();
		EncryptedBucket[] encryptedPaths = encryptedStashesAndPaths.getPaths();

		int offset = 0;

		//Serialize encrypted stashes
		byte[] nStashes = toBytes(encryptedStashes.length);
		System.arraycopy(nStashes, 0, result, offset, nStashes.length);
		offset += nStashes.length;

		for (EncryptedStash encryptedStash : encryptedStashes) {
			offset = serializeEncryptedStash(encryptedStash, result, offset);
		}

		//Serialize encrypted paths
		byte[] nPaths = toBytes(encryptedPaths.length);
		System.arraycopy(nPaths, 0, result, offset, nPaths.length);
		offset += nPaths.length;

		for (EncryptedBucket encryptedBucket : encryptedPaths) {
			offset = serializeEncryptedBucket(encryptedBucket, result, offset);
		}

		return result;
	}

	public static EncryptedStashesAndPaths deserializeEncryptedPathAndStash(ORAMContext oramContext,
																			byte[] serializedEncryptedPathSize) {
		int offset = 0;
		byte[] nStashesBytes = new byte[4];
		System.arraycopy(serializedEncryptedPathSize, offset, nStashesBytes, 0, nStashesBytes.length);
		offset += nStashesBytes.length;
		int nStashes = toNumber(nStashesBytes);
		EncryptedStash[] encryptedStashes = new EncryptedStash[nStashes];
		for (int i = 0; i < nStashes; i++) {
			offset = deserializeEncryptedStash(serializedEncryptedPathSize, offset, encryptedStashes, i);
		}

		byte[] nPathsBytes = new byte[4];
		System.arraycopy(serializedEncryptedPathSize, offset, nPathsBytes, 0, nPathsBytes.length);
		offset += nPathsBytes.length;
		int nPaths = toNumber(nPathsBytes);
		EncryptedBucket[] encryptedPaths = new EncryptedBucket[nPaths];
		for (int i = 0; i < nPaths; i++) {
			offset = deserializeEncryptedBucket(serializedEncryptedPathSize, offset, encryptedPaths, i,
					oramContext.getBucketSize());
		}

		return new EncryptedStashesAndPaths(encryptedStashes, encryptedPaths);
	}

	public static byte[] serializeEncryptedPositionMaps(EncryptedPositionMaps encryptedPositionMaps) {
		int dataSize = encryptedPositionMaps.getSerializedSize();
		byte[] result = new byte[dataSize];
		int newVersionId = encryptedPositionMaps.getNewVersionId();
		Map<Integer, EncryptedPositionMap> encryptedPositionMapsMap = encryptedPositionMaps.getEncryptedPositionMaps();

		int offset = 0;

		//Serialize new version id
		byte[] newVersionIdBytes = toBytes(newVersionId);
		System.arraycopy(newVersionIdBytes, 0, result, offset, newVersionIdBytes.length);
		offset += newVersionIdBytes.length;

		//Serialize encrypted position maps
		byte[] nPositionMapsBytes = toBytes(encryptedPositionMapsMap.size());
		System.arraycopy(nPositionMapsBytes, 0, result, offset, nPositionMapsBytes.length);
		offset += nPositionMapsBytes.length;

		//Sort keys
		int[] keys = new int[encryptedPositionMapsMap.size()];
		int k = 0;
		for (Integer i : encryptedPositionMapsMap.keySet()) {
			keys[k++] = i;
		}
		Arrays.sort(keys);

		for (int key : keys) {
			byte[] keyBytes = toBytes(key);
			System.arraycopy(keyBytes, 0, result, offset, keyBytes.length);
			offset += keyBytes.length;
			EncryptedPositionMap encryptedPositionMap = encryptedPositionMapsMap.get(key);
			offset = serializeEncryptedPositionMap(encryptedPositionMap, result, offset);
		}

		return result;
	}

	public static EncryptedPositionMaps deserializeEncryptedPositionMaps(byte[] serializedEncryptedPositionMaps) {
		int offset = 0;
		byte[] newVersionIdBytes = new byte[4];
		System.arraycopy(serializedEncryptedPositionMaps, offset, newVersionIdBytes, 0, newVersionIdBytes.length);
		offset += newVersionIdBytes.length;
		int newVersionId = toNumber(newVersionIdBytes);

		byte[] nPositionMapsBytes = new byte[4];
		System.arraycopy(serializedEncryptedPositionMaps, offset, nPositionMapsBytes, 0, nPositionMapsBytes.length);
		offset += nPositionMapsBytes.length;
		int nPositionMaps = toNumber(nPositionMapsBytes);
		Map<Integer, EncryptedPositionMap> encryptedPositionMaps = new HashMap<>(nPositionMaps);
		while (nPositionMaps-- > 0) {
			byte[] keyBytes = new byte[4];
			System.arraycopy(serializedEncryptedPositionMaps, offset, keyBytes, 0, keyBytes.length);
			offset += keyBytes.length;
			int key = toNumber(keyBytes);
			offset = deserializeEncryptedPositionMap(serializedEncryptedPositionMaps, offset, encryptedPositionMaps,
					key);
		}

		return new EncryptedPositionMaps(newVersionId, encryptedPositionMaps);
	}

	private static int serializeEncryptedStash(EncryptedStash encryptedStash, byte[] outputArray, int offset) {
		byte[] encryptedStashBlocks = encryptedStash.getEncryptedStash();
		byte[] sizeEncryptedStashBlocks;
		if (encryptedStashBlocks == null) {
			sizeEncryptedStashBlocks = toBytes(-1);
		} else {
			sizeEncryptedStashBlocks = toBytes(encryptedStashBlocks.length);
		}
		System.arraycopy(sizeEncryptedStashBlocks, 0, outputArray, offset, sizeEncryptedStashBlocks.length);
		offset += sizeEncryptedStashBlocks.length;
		if (encryptedStashBlocks != null) {
			System.arraycopy(encryptedStashBlocks, 0, outputArray, offset, encryptedStashBlocks.length);
			offset += encryptedStashBlocks.length;
		}

		return offset;
	}

	private static int deserializeEncryptedStash(byte[] inputArray, int offset, EncryptedStash[] result, int index) {
		byte[] sizeEncryptedStashBlocksBytes = new byte[4];
		System.arraycopy(inputArray, offset, sizeEncryptedStashBlocksBytes, 0, sizeEncryptedStashBlocksBytes.length);
		offset += sizeEncryptedStashBlocksBytes.length;
		int sizeEncryptedStashBlocks = toNumber(sizeEncryptedStashBlocksBytes);
		if (sizeEncryptedStashBlocks == -1) {
			result[index] = new EncryptedStash();
		} else {
			byte[] encryptedStashBlocks = new byte[sizeEncryptedStashBlocks];
			System.arraycopy(inputArray, offset, encryptedStashBlocks, 0, encryptedStashBlocks.length);
			offset += encryptedStashBlocks.length;
			result[index] = new EncryptedStash(encryptedStashBlocks);
		}
		return offset;
	}

	private static int serializeEncryptedBucket(EncryptedBucket encryptedBucket, byte[] outputArray, int offset) {
		if (encryptedBucket == null) {
			outputArray[offset] = 0;
		} else {
			outputArray[offset] = 1;
		}
		offset += 1;
		if (encryptedBucket != null) {
			byte[][] blocks = encryptedBucket.getBlocks();
			for (byte[] block : blocks) {
				byte[] sizeBlock = toBytes(block.length);
				System.arraycopy(sizeBlock, 0, outputArray, offset, sizeBlock.length);
				offset += sizeBlock.length;
				System.arraycopy(block, 0, outputArray, offset, block.length);
				offset += block.length;
			}
		}
		return offset;
	}

	private static int deserializeEncryptedBucket(byte[] inputArray, int offset, EncryptedBucket[] result, int index,
												  int bucketSize) {
		byte[] isNullBytes = new byte[1];
		System.arraycopy(inputArray, offset, isNullBytes, 0, isNullBytes.length);
		offset += isNullBytes.length;
		if (isNullBytes[0] == 0) {
			result[index] = null;
		} else {
			byte[][] blocks = new byte[bucketSize][];
			for (int j = 0; j < blocks.length; j++) {
				byte[] sizeBlockBytes = new byte[4];
				System.arraycopy(inputArray, offset, sizeBlockBytes, 0, sizeBlockBytes.length);
				offset += sizeBlockBytes.length;
				int sizeBlock = toNumber(sizeBlockBytes);
				byte[] block = new byte[sizeBlock];
				System.arraycopy(inputArray, offset, block, 0, block.length);
				offset += block.length;
				blocks[j] = block;
			}
			result[index] = new EncryptedBucket(blocks);
		}

		return offset;
	}

	private static int serializeEncryptedPositionMap(EncryptedPositionMap encryptedPositionMap, byte[] outputArray,
													 int offset) {
		byte[] encryptedPositionMapBytes = encryptedPositionMap.getEncryptedPositionMap();
		byte[] sizeEncryptedPositionMapBytes = toBytes(encryptedPositionMapBytes.length);
		System.arraycopy(sizeEncryptedPositionMapBytes, 0, outputArray, offset, sizeEncryptedPositionMapBytes.length);
		offset += sizeEncryptedPositionMapBytes.length;
		System.arraycopy(encryptedPositionMapBytes, 0, outputArray, offset, encryptedPositionMapBytes.length);
		offset += encryptedPositionMapBytes.length;
		return offset;
	}

	private static int deserializeEncryptedPositionMap(byte[] inputArray, int offset,
													   Map<Integer, EncryptedPositionMap> result, int key) {
		byte[] sizeEncryptedPositionMapBytes = new byte[4];
		System.arraycopy(inputArray, offset, sizeEncryptedPositionMapBytes, 0, sizeEncryptedPositionMapBytes.length);
		offset += sizeEncryptedPositionMapBytes.length;
		int sizeEncryptedPositionMap = toNumber(sizeEncryptedPositionMapBytes);
		byte[] encryptedPositionMapBytes = new byte[sizeEncryptedPositionMap];
		System.arraycopy(inputArray, offset, encryptedPositionMapBytes, 0, encryptedPositionMapBytes.length);
		offset += encryptedPositionMapBytes.length;
		result.put(key, new EncryptedPositionMap(encryptedPositionMapBytes));
		return offset;
	}
}
