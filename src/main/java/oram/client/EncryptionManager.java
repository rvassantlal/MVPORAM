package oram.client;

import oram.client.structure.PositionMap;
import oram.server.structure.EncryptedPositionMap;
import security.EncryptionAbstraction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

public class EncryptionManager {
	private final EncryptionAbstraction encryptionAbstraction;

	public EncryptionManager() {
		this.encryptionAbstraction = new EncryptionAbstraction("oram");
	}

	public PositionMap[] decryptPositionMaps(byte[] serializedEncryptedPositionMaps) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedEncryptedPositionMaps);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			int nPositionMaps = in.readInt();
			PositionMap[] positionMaps = new PositionMap[nPositionMaps];
			EncryptedPositionMap encryptedPositionMap;
			for (int i = 0; i < nPositionMaps; i++) {
				encryptedPositionMap = new EncryptedPositionMap();
				encryptedPositionMap.readExternal(in);
				positionMaps[i] = decryptPositionMap(encryptedPositionMap);
			}
			return positionMaps;
		} catch (IOException | ClassNotFoundException e) {
			return null;
		}
	}

	private PositionMap decryptPositionMap(EncryptedPositionMap encryptedPositionMap) {
		byte[] serializedPositionMap = encryptionAbstraction.decrypt(encryptedPositionMap.getEncryptedPositionMap());
		//TODO
		return new PositionMap();
	}

	public EncryptedPositionMap encryptPositionMap(PositionMap positionMap) {
		//TODO
		return null;
	}
}
