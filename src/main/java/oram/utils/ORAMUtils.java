package oram.utils;

import oram.messages.ORAMMessage;

import java.security.SecureRandom;
import java.util.*;

public class ORAMUtils {
	public static final int PASSWORD_LENGTH = 24;
	public static final int DUMMY_VERSION = -1;
	public static final int DUMMY_LOCATION = -1;
	public static final int DUMMY_ADDRESS = -1;
	public static final int BLOCK_IN_STASH = -2;
	public static final byte[] DUMMY_BLOCK = new byte[0];

	public static String generateRandomPassword(SecureRandom rndGenerator) {
		int delta = '~' - '!';
		StringBuilder pwd = new StringBuilder(PASSWORD_LENGTH);
		for (int i = 0; i < PASSWORD_LENGTH; i++) {
			char c = (char) (rndGenerator.nextInt(delta) + '!');
			pwd.append(c);
		}
		return pwd.toString();
	}

	public static int computeNumberOfSlots(int treeHeight, int bucketSize) {
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
		byte[] serializedRequest = new byte[dataSize];
		serializedRequest[0] = (byte) operation.ordinal();

		int offset = request.writeExternal(serializedRequest, 1);
		if (offset != dataSize)
			throw new RuntimeException("Failed to serialize request type " + operation);
		return serializedRequest;
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

	public static long computeDatabaseSize(int treeHeight, int blockSize) {
		return (long) computeNumberOfNodes(treeHeight) * blockSize;
	}

	public static void serializeInteger(int value, byte[] output, int startOffset) {
		byte[] valueBytes = toBytes(value);
		System.arraycopy(valueBytes, 0, output, startOffset, 4);
	}

	public static int deserializeInteger(byte[] input, int startOffset) {
		byte[] valueBytes = new byte[4];
		System.arraycopy(input, startOffset, valueBytes, 0, 4);
		return toNumber(valueBytes);
	}

	public static int[] convertSetIntoOrderedArray(Set<Integer> values) {
		int[] orderedValues = new int[values.size()];
		int index = 0;
		for (Integer value : values) {
			orderedValues[index++] = value;
		}
		Arrays.sort(orderedValues);
		return orderedValues;
	}

	public static int computeHashCode(int[] values) {
		int hash = values.length * 7;
		for (int value : values) {
			hash = hash * 31 + value;
		}
		return hash;
	}

	public static int computeHashCode(byte[] values) {
		int hash = values.length * 7;
		for (int value : values) {
			hash = hash * 31 + value;
		}
		return hash;
	}
}
