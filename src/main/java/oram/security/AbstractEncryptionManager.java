package oram.security;

import confidential.client.Response;
import oram.client.structure.*;
import oram.server.structure.*;
import oram.utils.ORAMContext;
import oram.utils.ORAMUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractEncryptionManager {
    protected final Logger logger = LoggerFactory.getLogger("oram");
    private static final byte[] iv = {109, 15, 57, 79, 75, 112, 50, 91, 18, 18, 107, 127, 65, 68, 12, 69};
    private final byte[] salt = {0x56, 0x1a, 0x7e, 0x23, (byte) 0xb3, 0x21, 0x12, (byte) 0xf6, (byte) 0xe1, 0x4d, 0x58,
            (byte) 0xd9, 0x0a, 0x59, (byte) 0xee, (byte) 0xe5, 0x3b, 0x61, 0x78, 0x27, 0x1e, (byte) 0xad, 0x52, 0x41,
            0x2c, 0x4b, (byte) 0xb6, 0x7b, (byte) 0xcd, 0x3a, (byte) 0xe9, (byte) 0x9c};
    private final IvParameterSpec initializationVector;
    private final SecretKeyFactory kf;
    private final Cipher cipher;

    public AbstractEncryptionManager() {
        this.initializationVector = new IvParameterSpec(iv);
        try {
            this.kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to create secret key factory", e);
        }
        try {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize cipher", e);
        }
    }

    public abstract String generatePassword();
    public abstract SecretKey createSecretKey(String password);

    protected SecretKey createSecretKey(char[] password) {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, 65536,256);
        try {
            return new SecretKeySpec(kf.generateSecret(keySpec).getEncoded(),"AES");
        } catch (Exception e) {
            logger.error("Failed to create secret key", e);
            return null;
        }
    }

    public abstract PositionMaps decryptPositionMapsResponse(Response response);
    public abstract StashesAndPaths decryptStashesAndPathsResponse(ORAMContext oramContext, Response response);

    public EncryptedPositionMap encryptPositionMap(SecretKey encryptionKey, PositionMap positionMap) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bos)) {
            positionMap.writeExternal(out);
            out.flush();
            bos.flush();
            byte[] serializedPositionMap = bos.toByteArray();
            byte[] encryptedPositionMap = encrypt(encryptionKey, serializedPositionMap);
            //logger.info("Position map size: {} bytes", serializedPositionMap.length);
            //logger.info("Encrypted position map size: {} bytes", encryptedPositionMap.length);
            return new EncryptedPositionMap(encryptedPositionMap);
        } catch (IOException e) {
            logger.error("Failed to encrypt position map", e);
            return null;
        }
    }

    protected EncryptedPositionMaps deserializeEncryptedPositionMaps(byte[] serializedEncryptedPositionMaps) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedEncryptedPositionMaps);
             DataInputStream in = new DataInputStream(bis)) {
            EncryptedPositionMaps encryptedPositionMaps = new EncryptedPositionMaps();
            encryptedPositionMaps.readExternal(in);
            // logger.info("{} Encrypted position maps size: {} bytes", encryptedPositionMaps.getEncryptedPositionMaps().size(),serializedEncryptedPositionMaps.length);
            return encryptedPositionMaps;
        } catch (IOException e) {
            logger.error("Failed to decrypt position map", e);
            return null;
        }
    }

    protected EncryptedStashesAndPaths deserializeEncryptedStashesAndPaths(ORAMContext oramContext, byte[] serializedEncryptedStashesAndPaths) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedEncryptedStashesAndPaths);
             DataInputStream in = new DataInputStream(bis)) {
            //logger.info("Encrypted paths and stashes size: {} bytes", serializedEncryptedStashesAndPaths.length);
            EncryptedStashesAndPaths encryptedStashesAndPaths = new EncryptedStashesAndPaths(oramContext);
            encryptedStashesAndPaths.readExternal(in);

            return encryptedStashesAndPaths;
        } catch (IOException e) {
            logger.error("Failed to decrypt stashes and paths", e);
            return null;
        }
    }

    public PositionMap decryptPositionMap(SecretKey decryptionKey, EncryptedPositionMap encryptedPositionMap) {
        byte[] serializedPositionMap = decrypt(decryptionKey, encryptedPositionMap.getEncryptedPositionMap());
        if (serializedPositionMap == null) {
            logger.error("Failed to deserialize position map");
            return null;
        }
        PositionMap deserializedPositionMap = new PositionMap();
        try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedPositionMap);
             DataInputStream in = new DataInputStream(bis)) {
            deserializedPositionMap.readExternal(in);
        } catch (IOException e) {
            logger.error("Failed to decrypt position map", e);
            return null;
        }
        return deserializedPositionMap;
    }

    public Map<Integer, EncryptedBucket> encryptPath(SecretKey encryptionKey, ORAMContext oramContext, Map<Integer, Bucket> path) {
        Map<Integer, EncryptedBucket> encryptedPath = new HashMap<>(path.size());
        for (Map.Entry<Integer, Bucket> entry : path.entrySet()) {
            Bucket bucket = entry.getValue();
            EncryptedBucket encryptedBucket = encryptBucket(encryptionKey, oramContext, bucket);
            encryptedPath.put(entry.getKey(), encryptedBucket);
        }
        return encryptedPath;
    }

    private EncryptedBucket encryptBucket(SecretKey encryptionKey, ORAMContext oramContext, Bucket bucket) {
        prepareBucket(oramContext, bucket);
        Block[] bucketContents = bucket.readBucket();
        byte[][] encryptedBlocks = new byte[bucketContents.length][];
        for (int i = 0; i < bucketContents.length; i++) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 DataOutputStream out = new DataOutputStream(bos)) {
                bucketContents[i].writeExternal(out);
                out.flush();
                bos.flush();
                encryptedBlocks[i] = encrypt(encryptionKey, bos.toByteArray());
            } catch (IOException e) {
                logger.error("Failed to encrypt bucket", e);
                return null;
            }
        }
        return new EncryptedBucket(encryptedBlocks);
    }

    public Bucket decryptBucket(SecretKey decryptionKey, ORAMContext oramContext, EncryptedBucket encryptedBucket) {
        if (encryptedBucket == null)
            return null;
        byte[][] blocks = encryptedBucket.getBlocks();
        Bucket newBucket = new Bucket(oramContext.getBucketSize(), oramContext.getBlockSize());

        for (byte[] block : blocks) {
            byte[] serializedBlock = decrypt(decryptionKey, block);
            if (serializedBlock == null) {
                throw new IllegalStateException("Failed to decrypt block");
            }
            Block deserializedBlock = new Block(oramContext.getBlockSize());
            try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedBlock);
                 DataInputStream in = new DataInputStream(bis)) {
                deserializedBlock.readExternal(in);
            } catch (IOException e) {
                logger.error("Failed to decrypt block", e);
                return null;
            }
            if (deserializedBlock.getAddress() != ORAMUtils.DUMMY_ADDRESS
                    && !Arrays.equals(deserializedBlock.getContent(), ORAMUtils.DUMMY_BLOCK)) {
                newBucket.putBlock(deserializedBlock);
            }
        }
        return newBucket;
    }

    public EncryptedStash encryptStash(SecretKey encryptionKey, Stash stash) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(bos)) {
            stash.writeExternal(out);
            out.flush();
            bos.flush();
            return new EncryptedStash(encrypt(encryptionKey, bos.toByteArray()));
        } catch (IOException e) {
            logger.error("Failed to encrypt stash", e);
            return null;
        }
    }

    public Stash decryptStash(SecretKey decryptionKey, int blockSize, EncryptedStash encryptedStash) {
        byte[] serializedStash = decrypt(decryptionKey, encryptedStash.getEncryptedStash());
        Stash deserializedStash = new Stash(blockSize);
        if(serializedStash != null) {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(serializedStash);
                 DataInputStream in = new DataInputStream(bis)) {
                deserializedStash.readExternal(in);
            } catch (IOException e) {
                logger.error("Failed to decrypt stash", e);
                return null;
            }
        }
        return deserializedStash;
    }

    private byte[] decrypt(SecretKey key, byte[] strToDecrypt) {
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, initializationVector);
            return strToDecrypt == null ? null : cipher.doFinal(strToDecrypt);
        } catch (Exception e) {
            logger.error("Failed to decrypt", e);
        }
        return null;
    }

    private byte[] encrypt(SecretKey key, byte[] strToEncrypt) {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key, initializationVector);
            return cipher.doFinal(strToEncrypt);
        } catch (Exception e) {
            logger.error("Failed to encrypt", e);
        }
        return null;
    }

    private void prepareBucket(ORAMContext oramContext, Bucket bucket) {
        Block[] blocks = bucket.readBucket();
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i] == null) {
                blocks[i] = new Block(oramContext.getBlockSize(), ORAMUtils.DUMMY_ADDRESS, ORAMUtils.DUMMY_VERSION,
                        ORAMUtils.DUMMY_BLOCK);
            }
        }
    }
}
