package oram;

import oram.messages.ORAMMessage;
import utils.Operation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

public class ORAMUtils {
	public static final int DUMMY_PATH = -1;
	public static final byte[] DUMMY_BLOCK = new byte[0];//TODO initialize correctly

	public static int computeNumberOfNodes(int treeHeight) {
		int nNodes = 0;
		for (int i = 0; i < treeHeight; i++) {
			nNodes += 1 << i;
		}
		return nNodes;
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
