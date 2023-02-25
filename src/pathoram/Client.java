package pathoram;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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

import org.apache.commons.lang3.SerializationUtils;

import structure.*;
import bftsmart.tom.ServiceProxy;

public class Client {
	private static final Integer TREE_SIZE = 7;
	private static final Integer TREE_LEVELS = (int)(Math.log(TREE_SIZE+1) / Math.log(2));
	private static Random r = new Random();
	private static SecretKey key;
	private static ServiceProxy pathOramProxy = null;
	public static void main(String[] args) throws NoSuchAlgorithmException {
		pathOramProxy = new ServiceProxy(Integer.parseInt(args[0]));
		KeyGenerator kg = KeyGenerator.getInstance("AES");
		kg.init(128);
		key = kg.generateKey();
		
		Scanner sc=new Scanner(System.in);
		Boolean execute=true;
		while(execute) {
			Operation op=null;
			while(op==null) {
				System.out.println("Choose operation (read or write)");
				String nextLine = sc.nextLine();
				if(nextLine.contentEquals("read"))
					op=Operation.READ;
				if(nextLine.contentEquals("write"))
					op=Operation.WRITE;
			}
			System.out.println("Insert key (is a short)");
			Short key = sc.nextShort();
			Short value = null;
			if(op.equals(Operation.WRITE)) {
				System.out.println("Insert new value (is a short)");
				value = sc.nextShort();
			}
			System.out.println("Answer from server: "+access(op, key, value));
			System.out.println("Do you want to exit (write yes to exit)?");
			String nextLine = sc.nextLine();
			if(nextLine.contentEquals("yes")) {
				sc.close();
				execute=false;
			}
		}
	}
	private static Short access(Operation op,Short a, Short newData) {
		TreeMap<Short,Integer> positionMap = SerializationUtils.deserialize(
				decrypt(pathOramProxy.invokeOrdered(
						SerializationUtils.serialize(ServerOperationType.GET_POSITION_MAP))));
		Integer oldPosition = positionMap.get(a);
		if(oldPosition==null)
			oldPosition=r.nextInt(TREE_SIZE/2+1);
		positionMap.put(a, r.nextInt(TREE_SIZE/2+1));
		TreeMap<Short, Short> stash=SerializationUtils.deserialize(
				decrypt(pathOramProxy.invokeOrdered(
						SerializationUtils.serialize(ServerOperationType.GET_STASH))));
		List<Bucket> path = new ArrayList<>(1);
		if(oldPosition!=null) {
			try {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
	            ObjectOutputStream oout = new ObjectOutputStream(out);
				oout.writeInt(ServerOperationType.GET_DATA);
				oout.writeInt(oldPosition);
				path = ((List<byte[]>) SerializationUtils.deserialize(pathOramProxy.invokeOrdered(out.toByteArray()))).stream()
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
		for (int level = TREE_LEVELS-1; level >= 0; level--) {
			int l = level;
			
			List<Integer> compatiblePaths = checkPaths(oldPosition,l);
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
	        oout.write(SerializationUtils.serialize(obj));
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
	 
	
	private static byte[] decrypt(byte[] strToDecrypt) {
	    try {
	    	Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		    cipher.init(Cipher.DECRYPT_MODE, key);
			return cipher.doFinal(strToDecrypt);
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
		}
		return null;
	}
	private static byte[] encrypt(byte[] strToEncrypt) {
	    try {
	    	Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		    cipher.init(Cipher.ENCRYPT_MODE, key);
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
		}
		return null;
	}
	private static List<Integer> checkPaths(Integer oldPosition, int level) {
		ArrayList<Integer> a = new ArrayList<Integer>();
		if(level==TREE_LEVELS-1) {
			a.add(oldPosition);
		}else {
			for (int i = 0; i < TREE_SIZE/2+1; i++) {
				if((int)(i/Math.pow(2,(TREE_LEVELS-1-level)))
						==(int)(oldPosition/Math.pow(2,(TREE_LEVELS-1-level))))
					a.add(i);
			}
		}
		return a;
	}
}
