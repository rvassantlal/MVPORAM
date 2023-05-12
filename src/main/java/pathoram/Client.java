package pathoram;


import bftsmart.tom.ServiceProxy;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.Pair;
import security.EncryptionAbstraction;
import clientStructure.*;
import utils.*;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class Client {
	private SecureRandom r = new SecureRandom();

	private ServiceProxy pathOramProxy;
	private EncryptionAbstraction encryptor;
	private int oramName;

	private int tree_size=-1;

	protected Client (int name,String pass){
		pathOramProxy = new ServiceProxy(name);
		encryptor = new EncryptionAbstraction(pass);
	}
	protected void associateOram(int oramName) {
		this.oramName=oramName;
	}
	protected boolean createOram(int size, int oramName) {
		this.oramName=oramName;
		List<Integer> reqArgs= Arrays.asList(oramName,
				ServerOperationType.CREATE_ORAM,
				size);
		return SerializationUtils.deserialize(pathOramProxy.invokeUnordered(createRequest(reqArgs)));

	}
	protected Short access(Operation op,Short a, Short newData, Boolean debugMode) throws IOException, ClassNotFoundException {
		if (debugMode)
			debugPrintTree();
		List<ClientPositionMap> temppositionMapClients;
		byte[] rawPositionMaps = pathOramProxy.invokeUnordered(createRequest(Arrays.asList(oramName,ServerOperationType.GET_POSITION_MAP)));
		ByteArrayInputStream in = new ByteArrayInputStream(rawPositionMaps);
		ObjectInputStream ois = new ObjectInputStream(in);
		tree_size = ois.readInt();
		List<byte[]> posMaps = (List<byte[]>) ois.readObject();
		temppositionMapClients = new ArrayList<>();
		if(posMaps.size()>0)
			temppositionMapClients =posMaps.stream().map(pm ->pm.length==0?null:(TreeMap<Short, Integer>)SerializationUtils.deserialize(encryptor.decrypt(pm)))
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		List<Double> snapIds = (List<Double>) ois.readObject();
		TreeMap<Double,Integer> oldPositions= new TreeMap<>();
		ClientPositionMap currentpositionMapClient = new ClientPositionMap();
		ClientStash clientStash = new ClientStash();
		for (ClientPositionMap map : temppositionMapClients) {
			for (Entry<Short, Integer> entry : map.entrySet()) {
				Short key = entry.getKey();
				if (key.equals(a)) {
					Integer val = entry.getValue();
					oldPositions.put(snapIds.get(temppositionMapClients.indexOf(map)),val);
				}
					
					/*if (currentpositionMap.containsKey(key) && !currentpositionMap.get(key).contains(val)) {
						currentpositionMap.get(key).add(val);
					} else {
						ArrayList<Integer> al=new ArrayList<>();
						al.add(val);
						currentpositionMap.put(key, al);
					}*/
			}
			currentpositionMapClient.putAll(map);
		}

		if((oldPositions.size()==0 && op==Operation.READ) || tree_size==-1) {
			return null;
		}
		int tree_levels = (int)(Math.log(tree_size+1) / Math.log(2));
		TreeMap<Short, Integer> tempPositionMap = SerializationUtils.clone(currentpositionMapClient);
		if(oldPositions.size()==0){
			oldPositions.put(0.0,r.nextInt(tree_size/2+1));
		}
		tempPositionMap.put(a, r.nextInt(tree_size/2+1));
		ByteArrayOutputStream pathout = new ByteArrayOutputStream();
		try {
			ObjectOutputStream pathoout = new ObjectOutputStream(pathout);
			pathoout.writeInt(oramName);
			pathoout.writeInt(ServerOperationType.GET_PATH_STASH);
			pathoout.writeObject(snapIds);
			pathoout.writeObject(new ArrayList<Integer>(oldPositions.values()));
			pathoout.flush();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		byte[] answer = pathOramProxy.invokeUnordered(pathout.toByteArray());
		ByteArrayInputStream in2 = new ByteArrayInputStream(answer);
		ObjectInputStream ois2 = new ObjectInputStream(in2);
		if (!ois2.readBoolean())
			return null;
		for (int i = 0; i < snapIds.size(); i++) {
			TreeMap<Double,byte[]> rawStash = null;
			TreeMap<Double,byte[]> rawPath = null;
			try {
				rawStash =(TreeMap<Double,byte[]>) ois2.readObject();
				rawPath = (TreeMap<Double,byte[]>) ois2.readObject();
			} catch (IOException | ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			TreeMap<Short, Pair<Double, Short>> intermediateStash = new TreeMap<Short, Pair<Double, Short>>();
			rawStash.forEach((key,val) -> {
				if(val.length>0)
					mergeStashes(intermediateStash,(TreeMap<Short, Short>) SerializationUtils.deserialize(encryptor.decrypt(val)),key);}
			);
			intermediateStash.forEach((key,val) -> clientStash.put(key,val.getRight()));

			TreeMap<Short, Pair<Double, Short>> intermediatePath = new TreeMap<>();
			rawPath.forEach((key,val) -> {
				if(val.length>0)
					mergePaths(intermediatePath,
							(Bucket) SerializationUtils.deserialize(encryptor.decrypt(val)), key);});
			intermediatePath.forEach((key,val) -> clientStash.put(key,val.getRight()));
		}
		Short data = clientStash.get(a);
		Integer oldPosition = oldPositions.get(oldPositions.lowerKey(Double.MAX_VALUE));
		if(op.equals(Operation.WRITE)) {
			clientStash.put(a,newData);
			data=newData;
		}
		Set<Entry<Short, Short>> stashValues = clientStash.entrySet();
		TreeMap<Integer,byte[]> newPath = new TreeMap<>();
		for (int level = tree_levels-1; level >= 0; level--) {
			List<Integer> compatiblePaths = checkPaths(oldPosition, level,tree_levels);
			ClientStash tempClientStash = stashValues.parallelStream()
					.filter(val -> compatiblePaths.contains(tempPositionMap.get(val.getKey())))
					.limit(Bucket.MAX_SIZE)
					.collect(Collectors.toMap(Entry::getKey, Entry::getValue));
			for (Short key : tempClientStash.keySet()) {
				clientStash.remove(key);
			}
			Bucket b = new Bucket();
			b.writeBucket(tempClientStash);

			newPath.put(level, encryptor.encrypt(SerializationUtils.serialize(b)));
		}
		if (debugMode)
			debugPrintPositionMap(tempPositionMap);

		try {
			ByteArrayOutputStream evictout = new ByteArrayOutputStream();
			ObjectOutputStream evictoout = new ObjectOutputStream(evictout);
			evictoout.writeInt(oramName);
			evictoout.writeInt(ServerOperationType.EVICT);
			evictoout.writeObject(snapIds);
			FourTuple<byte[], byte[], Integer, TreeMap<Integer,byte[]>> obj=new FourTuple<>(encryptor.encrypt(SerializationUtils.serialize(tempPositionMap)),
					encryptor.encrypt(SerializationUtils.serialize(clientStash)), oldPosition,
					newPath);
			evictoout.writeObject(obj);
			evictoout.flush();
			pathOramProxy.invokeOrdered(evictout.toByteArray());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return data;
	}

	private void mergePaths(TreeMap<Short, Pair<Double, Short>> intermediatePath, Bucket deserializedBucket, Double snapId) {
		Block[] elements = deserializedBucket.readBucket();
		elements.forEach((key, value) -> {
			if (!intermediatePath.containsKey(key) || intermediatePath.get(key).getKey() < snapId) {
				intermediatePath.put(key, Pair.of(snapId, value));
			}
		});
	}

	private void mergeStashes(TreeMap<Short, Pair<Double, Short>> intermediateStash, TreeMap<Short, Short> deserializedStash, Double snapId) {
		deserializedStash.forEach((key, value) -> {
			if (!intermediateStash.containsKey(key) || intermediateStash.get(key).getKey() < snapId) {
				intermediateStash.put(key, Pair.of(snapId, value));
			}
		});
	}


	private void debugPrintPositionMap(TreeMap<Short, Integer> tempPositionMap) {
		System.out.println("positionMap"+tempPositionMap.entrySet().stream()
				.map(bvalue-> bvalue.getKey()+";"+bvalue.getValue())
				.collect(Collectors.toList()));

	}
	private void debugPrintTree() throws IOException, ClassNotFoundException {
		byte[] rawTree = pathOramProxy.invokeUnordered(
				createRequest(Arrays.asList(oramName,ServerOperationType.GET_TREE)));
		if (rawTree!=null) {
			ByteArrayInputStream in = new ByteArrayInputStream(rawTree);
			ObjectInputStream ois = new ObjectInputStream(in);
			
			List<List<byte[]>> obj = ((List<List<byte[]>>) ois.readObject());
			obj.stream().forEach(treeList ->  auxPrintTree(treeList));
		}
	}
	private void auxPrintTree (List<byte[]> rawTree) {
		if(rawTree!=null) {
			List<String> tree = rawTree.stream()
					.map(b -> b==null ? null : (SerializationUtils.deserialize(encryptor.decrypt(b))).toString())
					.collect(Collectors.toList());
			TreePrinter.print(tree);
		}
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

	private List<Integer> checkPaths(Integer oldPosition, int level, int tree_levels) {
		ArrayList<Integer> a = new ArrayList<>();
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
