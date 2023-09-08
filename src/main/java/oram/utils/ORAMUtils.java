package oram.utils;

import oram.messages.ORAMMessage;
import org.apache.commons.lang3.tuple.Triple;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ORAMUtils {
	public static final int PASSWORD_LENGTH = 24;
	public static final int DUMMY_VERSION = 0;
	public static final int DUMMY_PATH = -1;
	public static final int DUMMY_ADDRESS = -1;
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

	public static int computeNumberOfNodes(int treeHeight) {
		int nNodes = 0;
		for (int i = 0; i <= treeHeight; i++) {
			nNodes += 1 << i;
		}
		return nNodes;
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

	public static Triple<Integer, Integer, Double> getStatisticsFromList(List<Integer> timeList) {
		double sum = 0;
		int max = -1;
		int min = Integer.MAX_VALUE;
		for (Integer getORAMTime : timeList) {
			sum += getORAMTime;
			if(getORAMTime > max){
				max = getORAMTime;
			}
			if(getORAMTime < min){
				min = getORAMTime;
			}
		}
		Double average =  sum / timeList.size();
		timeList.clear();
		return Triple.of(min,max,average);
	}

	public static byte[] serializeRequest(ServerOperationType operation, ORAMMessage request) {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutputStream out = new ObjectOutputStream(bos)) {
			out.write(operation.ordinal());
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
}
