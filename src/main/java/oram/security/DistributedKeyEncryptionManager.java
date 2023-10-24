package oram.security;

import confidential.client.Response;
import oram.client.structure.*;
import oram.server.structure.*;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DistributedKeyEncryptionManager extends AbstractEncryptionManager {
    private final SecureRandom rndGenerator;

    public DistributedKeyEncryptionManager() {
        this.rndGenerator = new SecureRandom("oram".getBytes());
    }

    @Override
    public String generatePassword() {
        return ORAMUtils.generateRandomPassword(rndGenerator);
    }

    @Override
    public SecretKey createSecretKey(String password) {
        return createSecretKey(password.toCharArray());
    }

    @Override
    public PositionMaps decryptPositionMapsResponse(Response response) {
        byte[][] confidentialData = response.getConfidentialData();
        if(response.getPlainData() == null || confidentialData == null || confidentialData.length == 0)
            return null;
        SecretKey[] decryptionKeys = new SecretKey[confidentialData.length];
        for (int i = 0; i < confidentialData.length; i++) {
            String password = new String(confidentialData[i]);
            decryptionKeys[i] = createSecretKey(password.toCharArray());
        }
        EncryptedPositionMaps encryptedPositionMaps = deserializeEncryptedPositionMaps(response.getPlainData());
        return decryptPositionMaps(decryptionKeys, encryptedPositionMaps);
    }

    @Override
    public StashesAndPaths decryptStashesAndPathsResponse(ORAMContext oramContext, Response response) {
        byte[][] confidentialData = response.getConfidentialData();
        if(response.getPlainData() == null || confidentialData == null || confidentialData.length == 0)
            return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(response.getPlainData());
             ObjectInputStream in = new ObjectInputStream(bis)) {
            byte[] serializedEncryptedStashesAndPaths = new byte[in.readInt()];
            in.readFully(serializedEncryptedStashesAndPaths);
            int nVersions = in.readInt();
            Map<Integer, SecretKey> decryptionKeys = new HashMap<>(nVersions);
            for (int i = 0; i < nVersions; i++) {
                int version = in.readInt();
                String password = new String(confidentialData[i]);
                SecretKey secretKey = createSecretKey(password.toCharArray());
                decryptionKeys.put(version, secretKey);
            }
            EncryptedStashesAndPaths encryptedStashesAndPaths = deserializeEncryptedStashesAndPaths(oramContext,
                    serializedEncryptedStashesAndPaths);
            return decryptStashesAndPaths(decryptionKeys, oramContext, encryptedStashesAndPaths);
        } catch (IOException e) {
            logger.error("Failed");
            return null;
        }
    }

    private StashesAndPaths decryptStashesAndPaths(Map<Integer, SecretKey> decryptionKeys, ORAMContext oramContext,
                                                   EncryptedStashesAndPaths encryptedStashesAndPaths) {
        Map<Integer, Stash> stashes = decryptStashes(decryptionKeys, oramContext.getBlockSize(),
                encryptedStashesAndPaths.getEncryptedStashes());
        Map<Integer, Bucket[]> paths = decryptPaths(decryptionKeys, oramContext, encryptedStashesAndPaths.getPaths());
        return new StashesAndPaths(stashes, paths);
    }

    private Map<Integer, Stash> decryptStashes(Map<Integer, SecretKey> decryptionKeys, int blockSize,
                                               Map<Integer, EncryptedStash> encryptedStashes) {
        Map<Integer, Stash> stashes = new HashMap<>(encryptedStashes.size());
        for (Map.Entry<Integer, EncryptedStash> entry : encryptedStashes.entrySet()) {
            int versionId = entry.getKey();
            SecretKey decryptionKey = decryptionKeys.get(versionId);
            stashes.put(entry.getKey(), decryptStash(decryptionKey, blockSize, entry.getValue()));
        }
        return stashes;
    }

    private Map<Integer, Bucket[]> decryptPaths(Map<Integer, SecretKey> decryptionKeys, ORAMContext oramContext,
                                                Map<Integer, EncryptedBucket[]> encryptedPaths) {
        Map<Integer, Bucket[]> paths = new HashMap<>(encryptedPaths.size());
        for (Map.Entry<Integer, EncryptedBucket[]> entry : encryptedPaths.entrySet()) {
            EncryptedBucket[] encryptedBuckets = entry.getValue();
            Bucket[] buckets = new Bucket[encryptedBuckets.length];
            SecretKey decryptionKey = decryptionKeys.get(entry.getKey());
            for (int i = 0; i < encryptedBuckets.length; i++) {
                buckets[i] = decryptBucket(decryptionKey, oramContext, encryptedBuckets[i]);
            }
            paths.put(entry.getKey(), buckets);
        }
        return paths;
    }

    private PositionMaps decryptPositionMaps(SecretKey[] decryptionKeys, EncryptedPositionMaps encryptedPositionMaps) {
        Map<Integer, EncryptedPositionMap> encryptedPMs = encryptedPositionMaps.getEncryptedPositionMaps();
        int[] orderedVersions = getOrderedKeys(encryptedPMs.keySet());
        Map<Integer, PositionMap> positionMaps = new HashMap<>(encryptedPMs.size());
        for (int i = 0; i < orderedVersions.length; i++) {
            int version = orderedVersions[i];
            EncryptedPositionMap encryptedPositionMap = encryptedPMs.get(version);
            SecretKey decryptionKey = decryptionKeys[i];
            positionMaps.put(version, decryptPositionMap(decryptionKey, encryptedPositionMap));

        }
        return new PositionMaps(encryptedPositionMaps.getNewVersionId(), positionMaps);
    }

    private static int[] getOrderedKeys(Set<Integer> keys) {
        int[] result = new int[keys.size()];
        int i = 0;
        for (Integer key : keys) {
            result[i++] = key;
        }
        Arrays.sort(result);
        return result;
    }
}
