package oram.security;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionAbstraction {
	private final Logger logger = LoggerFactory.getLogger("oram");
	private static final byte[] iv = {109, 15, 57, 79, 75, 112, 50, 91, 18, 18, 107, 127, 65, 68, 12, 69};

	public EncryptionAbstraction() {}

	public byte[] decrypt(SecretKey key, byte[] strToDecrypt) {
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, key,new IvParameterSpec(iv));
			return strToDecrypt==null? null :cipher.doFinal(strToDecrypt);
		} catch (Exception e) {
			logger.error("Failed to decrypt", e);
		}
		return null;
	}

	public byte[] encrypt(SecretKey key, byte[] strToEncrypt) {
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, key,new IvParameterSpec(iv));
			return cipher.doFinal(strToEncrypt);
		} catch (Exception e) {
			logger.error("Failed to encrypt", e);
		}
		return null;
	}
}
