package oram.security;

import confidential.client.Response;
import oram.client.structure.*;
import oram.server.structure.*;
import oram.utils.ORAMContext;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FixedKeyEncryptionManager extends AbstractEncryptionManager {
	private final SecretKey fixedSecretKey;
	private final String fixedPassword;

	public FixedKeyEncryptionManager() {
		this.fixedPassword = "oram";
		this.fixedSecretKey = createSecretKey(fixedPassword.toCharArray());
	}

	@Override
	public String generatePassword() {
		return fixedPassword;
	}

	@Override
	public SecretKey createSecretKey(String password) {
		return fixedSecretKey;
	}

	@Override
	public PositionMaps decryptPositionMapsResponse(Response response) {
		if (response.getPlainData() == null)
			return null;
		EncryptedPositionMaps encryptedPositionMaps = deserializeEncryptedPositionMaps(response.getPlainData());
		return decryptPositionMaps(encryptedPositionMaps);
	}

	@Override
	public StashesAndPaths decryptStashesAndPathsResponse(ORAMContext oramContext, Response response) {
		if (response.getPlainData() == null)
			return null;
		EncryptedStashesAndPaths encryptedStashesAndPaths = deserializeEncryptedStashesAndPaths(oramContext,
				response.getPlainData());
		return decryptStashesAndPaths(oramContext, encryptedStashesAndPaths);
	}

	private PositionMaps decryptPositionMaps(EncryptedPositionMaps encryptedPositionMaps) {
		Map<Integer, EncryptedPositionMap> encryptedPMs = encryptedPositionMaps.getEncryptedPositionMaps();
		Map<Integer, PositionMap> positionMaps = new HashMap<>(encryptedPMs.size());
		for (Map.Entry<Integer, EncryptedPositionMap> entry : encryptedPMs.entrySet()) {
			positionMaps.put(entry.getKey(), decryptPositionMap(fixedSecretKey, entry.getValue()));
		}
		return new PositionMaps(encryptedPositionMaps.getNewVersionId(), positionMaps);
	}

	private StashesAndPaths decryptStashesAndPaths(ORAMContext oramContext,
												  EncryptedStashesAndPaths encryptedStashesAndPaths) {
		Map<Integer, Stash> stashes = decryptStashes(oramContext.getBlockSize(),
				encryptedStashesAndPaths.getEncryptedStashes());
		Map<Integer, Bucket[]> paths = decryptPaths(oramContext, encryptedStashesAndPaths.getPaths());
		return new StashesAndPaths(stashes, paths);
	}

	private Map<Integer, Stash> decryptStashes(int blockSize, Map<Integer, EncryptedStash> encryptedStashes) {
		Map<Integer, Stash> stashes = new HashMap<>(encryptedStashes.size());
		for (Map.Entry<Integer, EncryptedStash> entry : encryptedStashes.entrySet()) {
			stashes.put(entry.getKey(), decryptStash(fixedSecretKey, blockSize, entry.getValue()));
		}
		return stashes;
	}

	private Map<Integer, Bucket[]> decryptPaths(ORAMContext oramContext, Map<Integer, EncryptedBucket[]> encryptedPaths) {
		Map<Integer, Bucket[]> paths = new HashMap<>(encryptedPaths.size());
		for (Map.Entry<Integer, EncryptedBucket[]> entry : encryptedPaths.entrySet()) {
			EncryptedBucket[] encryptedBuckets = entry.getValue();
			Bucket[] buckets = new Bucket[encryptedBuckets.length];
			for (int i = 0; i < encryptedBuckets.length; i++) {
				buckets[i] = decryptBucket(fixedSecretKey, oramContext, encryptedBuckets[i]);
			}
			paths.put(entry.getKey(), buckets);
		}
		return paths;
	}

}
