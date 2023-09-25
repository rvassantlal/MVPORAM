package pathoram;


import bftsmart.tom.ServiceProxy;
import org.apache.commons.lang3.SerializationUtils;
import structure.Bucket;
import structure.FourTuple;
import structure.Operation;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class Client {
	private SecureRandom r = new SecureRandom();
	private SecretKey key;
	private ServiceProxy pathOramProxy = null;
	private static final byte[] iv = {109,15,57,79,75,112,50,91,18,18,107,127,65,68,12,69};
	private byte[] salt = {0x56, 0x1a, 0x7e, 0x23, (byte) 0xb3, 0x21, 0x12, (byte) 0xf6, (byte) 0xe1, 0x4d, 0x58, (byte) 0xd9, 0x0a, 0x59, (byte) 0xee, (byte) 0xe5, 
			0x3b, 0x61, 0x78, 0x27, 0x1e, (byte) 0xad, 0x52, 0x41, 0x2c, 0x4b, (byte) 0xb6, 0x7b, (byte) 0xcd, 0x3a, (byte) 0xe9, (byte) 0x9c};
	private int oramName;
	private TreeMap<Short,Integer> currentpositionMap;
	private int positionMapVersion=0;
	private int tree_size=-1;
	
	protected Client (int name,String pass) throws NoSuchAlgorithmException, InvalidKeySpecException {
		pathOramProxy = new ServiceProxy(name);
		PBEKeySpec keySpec = new PBEKeySpec(pass.toCharArray(), salt, 65536,256);
		SecretKeyFactory kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
		key = new SecretKeySpec(kf.generateSecret(keySpec).getEncoded(),"AES");
		
	}
	protected void associateOram(int oramName) {
		this.oramName=oramName;
	}
	protected boolean createOram(int size, int oramName) {
		this.oramName=oramName;
		List<Integer> reqArgs= Arrays.asList(oramName,
				ServerOperationType.CREATE_ORAM,
				size);
		return (boolean) SerializationUtils.deserialize(pathOramProxy.invokeUnordered(createRequest(reqArgs)));

	}
	protected Short access(Operation op,Short a, Short newData) throws IOException {
		if(currentpositionMap==null) {
			byte[] rawPositionMap = pathOramProxy.invokeUnordered(createRequest(Arrays.asList(oramName,ServerOperationType.GET_POSITION_MAP)));
			ByteArrayInputStream in = new ByteArrayInputStream(rawPositionMap);
			ObjectInputStream ois = new ObjectInputStream(in);
			positionMapVersion = ois.readInt();
			tree_size = ois.readInt();
			try {
				currentpositionMap = positionMapVersion==0? new TreeMap<>():
					(TreeMap<Short, Integer>) SerializationUtils.deserialize(decrypt((byte[]) ois.readObject()));
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		Integer oldPosition = currentpositionMap.get(a);
		if((oldPosition==null && op==Operation.READ) || tree_size==-1) {
			return null;
		}
		int tree_levels = (int)(Math.log(tree_size+1) / Math.log(2));
		TreeMap<Short, Integer> tempPositionMap = SerializationUtils.clone(currentpositionMap);
		if(oldPosition==null) {
			oldPosition=r.nextInt(tree_size/2+1);
		}
		tempPositionMap.put(a, r.nextInt(tree_size/2+1));
		byte[] rawStash = null;
		ArrayList<byte[]> rawPath = new ArrayList<byte[]>();
		try {
			List<Integer> args = Arrays.asList(oramName,ServerOperationType.GET_PATH_STASH,
					positionMapVersion,oldPosition);
			byte[] answer = pathOramProxy.invokeUnordered(createRequest(args));
			if(answer==null) {
				currentpositionMap=null;
				return access(op,a,newData);
			}
			ByteArrayInputStream in = new ByteArrayInputStream(answer);
			ObjectInputStream ois = new ObjectInputStream(in);
			if(ois.readBoolean()) {
				rawStash = ois.readBoolean()?(byte[]) ois.readObject():null;
				rawPath = positionMapVersion==0 ? new ArrayList<>() : (ArrayList<byte[]>) ois.readObject();
			}
			else {
				System.out.println("The system is processing other requests, wait a moment (during path reading)");
				currentpositionMap=null;
				return access(op,a,newData);
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		TreeMap<Short, Short> stash = rawStash==null ? new TreeMap<Short,Short>():
			(TreeMap<Short, Short>) SerializationUtils.deserialize(decrypt(rawStash));
		List<Bucket> path =	rawPath.stream()
				.map(b -> b==null ? null : (Bucket)SerializationUtils.deserialize(decrypt(b)))
				.collect(Collectors.toList());


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
					.filter(val -> compatiblePaths.contains(tempPositionMap.get(val.getKey())))
					.limit(Bucket.MAX_SIZE)
					.collect(Collectors.toMap(val -> val.getKey(), val -> val.getValue()));
			for (Short key : tempStash.keySet()) {
				stash.remove(key);
			}
			Bucket b = new Bucket();
			b.writeBucket(tempStash);

			newPath.put(l, encrypt(SerializationUtils.serialize(b)));
		}
		//debugPrintPositionMap(tempPositionMap);
		
		try {
			ByteArrayOutputStream evictout = new ByteArrayOutputStream();
			ObjectOutputStream evictoout = new ObjectOutputStream(evictout);
			evictoout.writeInt(oramName);
			evictoout.writeInt(ServerOperationType.EVICT);
			evictoout.writeInt(positionMapVersion+1);
			FourTuple<byte[], byte[], Integer, TreeMap<Integer,byte[]>> obj=new FourTuple<byte[], byte[], Integer, TreeMap<Integer,byte[]>>(encrypt(SerializationUtils.serialize(tempPositionMap)),
					encrypt(SerializationUtils.serialize(stash)), oldPosition,
					newPath);
			evictoout.writeObject(obj);
			evictoout.flush();
			byte[] evictionAnswer = pathOramProxy.invokeOrdered(evictout.toByteArray());
			ByteArrayInputStream in = new ByteArrayInputStream(evictionAnswer);
			ObjectInputStream oin = new ObjectInputStream(in);
			if(!oin.readBoolean()) {
				System.out.println("The system is processing other requests, wait a moment (during eviction)");
				currentpositionMap=null;
				return access(op,a,newData);
			}
			else {
				currentpositionMap=tempPositionMap;
				positionMapVersion+=1;
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return data;
	}


	private void debugPrintPositionMap(TreeMap<Short, Integer> tempPositionMap) {
		System.out.println("positionMap"+tempPositionMap.entrySet().stream()
				.map(bvalue-> bvalue.getKey()+";"+bvalue.getValue())
				.collect(Collectors.toList()));
		
	}
	private byte[] createRequest(List<Integer> contents) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ObjectOutputStream oout = new ObjectOutputStream(out);
			for (Integer integer : contents) {
				oout.writeInt(integer);
			}
			oout.flush();
			return out.toByteArray();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;

	}
	private byte[] decrypt(byte[] strToDecrypt) {
		try {

			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, key,new IvParameterSpec(iv));
			byte[] answer = strToDecrypt==null? null :cipher.doFinal(strToDecrypt);
			return answer;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return null;
	}
	private byte[] encrypt(byte[] strToEncrypt) {
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
	private List<Integer> checkPaths(Integer oldPosition, int level, int tree_size, int tree_levels) {
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
