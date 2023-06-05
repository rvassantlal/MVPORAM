package oram;

import oram.messages.ORAMMessage;
import utils.Operation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ORAMUtils {
	public static final double DUMMY_VERSION = 0;
	public static final int DUMMY_PATH = -1;
	public static final int DUMMY_ADDRESS = -1;
	public static final byte[] DUMMY_BLOCK = new byte[0];//TODO initialize correctly

	public static int computeNumberOfNodes(int treeHeight) {
		int nNodes = 0;
		for (int i = 0; i <= treeHeight; i++) {
			nNodes += 1 << i;
		}
		return nNodes;
	}

	public static int[] computePathLocations(byte pathId, int treeHeight) {
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

	public static byte[] serializeRequest(Operation operation, ORAMMessage request) {
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
}
