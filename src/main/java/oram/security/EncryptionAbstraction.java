package oram.security;


import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class EncryptionAbstraction {
	private SecretKey key;
	private static final byte[] iv = {109,15,57,79,75,112,50,91,18,18,107,127,65,68,12,69};
	private byte[] salt = {0x56, 0x1a, 0x7e, 0x23, (byte) 0xb3, 0x21, 0x12, (byte) 0xf6, (byte) 0xe1, 0x4d, 0x58, (byte) 0xd9, 0x0a, 0x59, (byte) 0xee, (byte) 0xe5, 
			0x3b, 0x61, 0x78, 0x27, 0x1e, (byte) 0xad, 0x52, 0x41, 0x2c, 0x4b, (byte) 0xb6, 0x7b, (byte) 0xcd, 0x3a, (byte) 0xe9, (byte) 0x9c};

	public EncryptionAbstraction (String pass) {
		PBEKeySpec keySpec = new PBEKeySpec(pass.toCharArray(), salt, 65536,256);
		SecretKeyFactory kf;
		try {
			kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			key = new SecretKeySpec(kf.generateSecret(keySpec).getEncoded(),"AES");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	public byte[] decrypt(byte[] strToDecrypt) {
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, key,new IvParameterSpec(iv));
			return strToDecrypt==null? null :cipher.doFinal(strToDecrypt);
		} catch (Exception e) {
			// TODO Auto-generated catch block
		} 
		return null;
	}
	public byte[] encrypt(byte[] strToEncrypt) {
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, key,new IvParameterSpec(iv));
			return cipher.doFinal(strToEncrypt);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
