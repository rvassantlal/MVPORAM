package pathoram;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.SerializationUtils;

import structure.*;
import bftsmart.tom.ServiceProxy;

public class Client {
	private static SecureRandom r = new SecureRandom();
	private static SecretKey key;
	private static ServiceProxy pathOramProxy = null;
	private static byte[] iv = new byte[16];
	private static byte[] salt = new byte[32];
	
	public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeySpecException {
		pathOramProxy = new ServiceProxy(Integer.parseInt(args[0]));		
		Scanner sc=new Scanner(System.in);
		Boolean execute=true;
		System.out.println("Insert your password please:");
		String pass = sc.nextLine();
		r.nextBytes(salt);
		r.nextBytes(iv);
		PBEKeySpec keySpec = new PBEKeySpec(pass.toCharArray(), salt, 100,256); // pass, salt, iterations
		SecretKeyFactory kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
		key = new SecretKeySpec(kf.generateSecret(keySpec).getEncoded(),"AES");
		System.out.println("Do you already have an ORAM? (Y for yes, N for no)");
		String nLine = sc.nextLine();
		if(nLine.toUpperCase().contentEquals("N")) {
			System.out.println("Insert size:");
			int size = sc.nextInt();
			String msg=createOram(size)?"ORAM created! Session automatically opened for you.":"There was an error, this ORAM already exists!";
			System.out.println(msg);
		}else {
			System.out.println("Opening your session");
		}
		openSession();
		while(execute) {
			Operation op=null;
			while(op==null) {
				System.out.println("Choose operation (read or write)");
				String nextLine = sc.nextLine();
				if(nextLine.toLowerCase().contentEquals("read"))
					op=Operation.READ;
				if(nextLine.toLowerCase().contentEquals("write"))
					op=Operation.WRITE;
			}
			System.out.println("Insert key (is a short)");
			Short key = sc.nextShort();
			Short value = null;
			if(op.equals(Operation.WRITE)) {
				System.out.println("Insert new value (is a short)");
				value = sc.nextShort();
			}
			sc.nextLine();
			try {
				Short answer = access(op, key, value);
				System.out.println("Answer from server: "+answer);
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Your session is closed! Please open your session, please");
			}		
			System.out.println("Do you want to exit (write yes to exit)?");
			String nextLine = sc.nextLine();
			if(nextLine.toLowerCase().contentEquals("yes")) {
				closeSession();
				sc.close();
				execute=false;
			}
		}
	}
	private static void openSession() {
		pathOramProxy.invokeOrdered(createRequest(ServerOperationType.OPEN_SESSION));	
	}
	private static void closeSession() {
		pathOramProxy.invokeOrdered(createRequest(ServerOperationType.CLOSE_SESSION));	
	}
	private static boolean createOram(int size) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
	        ObjectOutputStream oout = new ObjectOutputStream(out);
			oout.writeInt(ServerOperationType.CREATE_ORAM);
			oout.writeInt(size);
			oout.flush();
			return (boolean) SerializationUtils.deserialize(pathOramProxy.invokeUnordered(out.toByteArray()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	private static Short access(Operation op,Short a, Short newData) throws IOException {
		byte[] rawPositionMap = pathOramProxy.invokeUnordered(createRequest(ServerOperationType.GET_POSITION_MAP));
		TreeMap<Short,Integer> positionMap = rawPositionMap==null? new TreeMap<Short,Integer>():
			(TreeMap<Short, Integer>) SerializationUtils.deserialize(decrypt(rawPositionMap));
		Integer oldPosition = positionMap.get(a);
		if(oldPosition==null && op==Operation.READ) {
			return null;
		}
		int tree_size = positionMap.size();
		int tree_levels = (int)(Math.log(tree_size+1) / Math.log(2));
		Boolean alreadyInORAM=true;
		if(oldPosition==null) {
			oldPosition=r.nextInt(tree_size/2+1);
			alreadyInORAM=false;
		}
		positionMap.put(a, r.nextInt(tree_size/2+1));
		byte[] rawStash=pathOramProxy.invokeUnordered(createRequest(ServerOperationType.GET_STASH));
		TreeMap<Short, Short> stash= rawStash==null ? new TreeMap<Short,Short>():
			(TreeMap<Short, Short>) SerializationUtils.deserialize(decrypt(rawStash));
		List<Bucket> path = new ArrayList<>(1);
		if(alreadyInORAM) {
			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
	            ObjectOutputStream oout = new ObjectOutputStream(out);
				oout.writeInt(ServerOperationType.GET_DATA);
				oout.writeInt(oldPosition);
				oout.flush();
				List<byte[]> list = (List<byte[]>) SerializationUtils.deserialize(pathOramProxy.invokeUnordered(out.toByteArray()));
				path =	list.stream()
						.map(b -> b==null ? null : (Bucket)SerializationUtils.deserialize(decrypt(b)))
						.collect(Collectors.toList());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            
		}
		for(Bucket b : path) {
			if(b!=null)
				stash.putAll(b.readBucket());
		}
		Short data = stash.get(a);
		if(op.equals(Operation.WRITE)) {
			stash.put(a,newData);
			data=newData;
		}
		Set<Entry<Short, Short>> stashValues = stash.entrySet();
		TreeMap<Integer,byte[]> newPath = new TreeMap<>();
		for (int level = tree_levels-1; level >= 0; level--) {
			int l = level;
			
			List<Integer> compatiblePaths = checkPaths(oldPosition,l,tree_size,tree_levels);
			Map<Short,Short> tempStash = stashValues.parallelStream()
					.filter(val -> compatiblePaths.contains(positionMap.get(val.getKey())))
					.limit(Bucket.MAX_SIZE)
					.peek(val2 -> stash.remove(val2.getKey()))
					.collect(Collectors.toMap(val -> val.getKey(), val -> val.getValue()));
			Bucket b = new Bucket();
			b.writeBucket(tempStash);
			
			System.out.println("OUT"+b.readBucket().entrySet().stream()
					.map(bvalue-> bvalue.getKey()+";"+bvalue.getValue())
					.collect(Collectors.toList()));
			
			newPath.put(l, encrypt(SerializationUtils.serialize(b)));
		}
		System.out.println("positionMap"+positionMap.entrySet().stream()
				.map(bvalue-> bvalue.getKey()+";"+bvalue.getValue())
				.collect(Collectors.toList()));
        try {
        	ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream oout = new ObjectOutputStream(out);
			oout.writeInt(ServerOperationType.EVICT);
			FourTuple<byte[], byte[], Integer, TreeMap<Integer,byte[]>> obj=new FourTuple<byte[], byte[], Integer, TreeMap<Integer,byte[]>>(encrypt(SerializationUtils.serialize(positionMap)),
					encrypt(SerializationUtils.serialize(stash)), oldPosition,
					newPath);
	        oout.writeObject(obj);
	        oout.flush();
	        out.flush();
			pathOramProxy.invokeOrdered(out.toByteArray());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
		/* FOR DEBUG PURPOSES, can be used in junit
		 * List<Bucket> tree = ((List<byte[]>) SerializationUtils.deserialize(pathOramProxy.invokeOrdered(
				SerializationUtils.serialize(ServerOperationType.GET_TREE)))).stream()
				.map(b -> b==null ? null : (Bucket)SerializationUtils.deserialize(decrypt(b)))
				.collect(Collectors.toList());
		System.out.println("TREE");
		for (Bucket bucket : tree) {
			if(bucket!=null) {
				System.out.println(bucket.readBucket().entrySet().stream()
						.map(bvalue-> bvalue.getKey()+";"+bvalue.getValue())
						.collect(Collectors.toList()));
			}else {
				System.out.println("null");
			}
				
			
		}*/
		return data;
	}
	 
	
	private static byte[] createRequest(int operation) {
        try {
        	ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream oout = new ObjectOutputStream(out);
            oout.writeInt(operation);
			oout.flush();
			return out.toByteArray();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
		
	}
	private static byte[] decrypt(byte[] strToDecrypt) {
	    try {
	    	
	    	Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		    cipher.init(Cipher.DECRYPT_MODE, key,new IvParameterSpec(iv));
			byte[] answer = strToDecrypt==null? null :cipher.doFinal(strToDecrypt);
			return answer;
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	private static byte[] encrypt(byte[] strToEncrypt) {
	    try {
	    	Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
		    cipher.init(Cipher.ENCRYPT_MODE, key,new IvParameterSpec(iv));
			return cipher.doFinal(strToEncrypt);
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	private static List<Integer> checkPaths(Integer oldPosition, int level, int tree_size, int tree_levels) {
		ArrayList<Integer> a = new ArrayList<Integer>();
		if(level==tree_levels-1) {
			a.add(oldPosition);
		}else {
			for (int i = 0; i < tree_size/2+1; i++) {
				if((int)(i/Math.pow(2,(tree_levels-1-level)))
						==(int)(oldPosition/Math.pow(2,(tree_levels-1-level))))
					a.add(i);
			}
		}
		return a;
	}
}
