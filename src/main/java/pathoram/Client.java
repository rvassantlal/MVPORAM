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
	private final int[] possibleTreeSizes={3,7,15,31,63,127,255,511};
	private ServiceProxy pathOramProxy;
	private EncryptionAbstraction encryptor;
	private int oramName;

	private int tree_size=-1;

	public Client(int name, String pass){
		pathOramProxy = new ServiceProxy(name);
		encryptor = new EncryptionAbstraction(pass);
	}
	public void associateOram(int oramName) {
		this.oramName=oramName;
	}
	public boolean createOram(int size, int oramName) {
		for (int i = 0; i < possibleTreeSizes.length; i++) {
			if(size==possibleTreeSizes[i]){
				this.tree_size=size;
				i=possibleTreeSizes.length;
			}
		}
		if (this.tree_size==-1)
			return false;
		this.oramName=oramName;
		List<Integer> reqArgs= Arrays.asList(oramName,
				ServerOperationType.CREATE_ORAM,
				size);
		return SerializationUtils.deserialize(pathOramProxy.invokeUnordered(createRequest(reqArgs)));

	}
	public byte[] readMemory(Integer position) throws IOException, ClassNotFoundException {
		return access(Operation.READ,position,null,false);
	}
	public byte[] writeMemory(Integer position,byte[] content) throws IOException, ClassNotFoundException {
		return access(Operation.WRITE,position,content,false);
	}
	public byte[] access(Operation op, Integer a, byte[] newData, Boolean debugMode) throws IOException, ClassNotFoundException {
		if (debugMode)
			debugPrintTree();
		byte[] rawPositionMaps = pathOramProxy.invokeUnordered(createRequest(Arrays.asList(oramName,ServerOperationType.GET_POSITION_MAP)));
		ByteArrayInputStream in = new ByteArrayInputStream(rawPositionMaps);
		ObjectInputStream ois = new ObjectInputStream(in);
		int paralellPathNumber = ois.readInt();
		List<ClientPositionMap> posMaps = new ArrayList<>();
		for (int i = 0; i < paralellPathNumber; i++) {
			ClientPositionMap p = new ClientPositionMap(tree_size);
			p.readExternal(ois);
			posMaps.add(p);
		}
		snapshotIdentifiers snapIds = new snapshotIdentifiers();
		snapIds.readExternal(ois);
		TreeMap<Double,Integer> oldPositions= new TreeMap<>();
		ClientPositionMap currentpositionMapClient = new ClientPositionMap(tree_size);
		ClientStash clientStash = new ClientStash();
		for (ClientPositionMap map : posMaps) {
			for (Entry<Integer, Integer> entry : map.entrySet()) {
				Integer key = entry.getKey();
				if (key.equals(a)) {
					Integer val = entry.getValue();
					oldPositions.put(snapIds.getByIndex(posMaps.indexOf(map)),val);
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
		ClientPositionMap tempPositionMap = SerializationUtils.clone(currentpositionMapClient);
		if(oldPositions.size()==0){
			oldPositions.put(0.0,r.nextInt(tree_size/2+1));
		}
		tempPositionMap.putInPosition(a, r.nextInt(tree_size/2+1));
		ByteArrayOutputStream pathout = new ByteArrayOutputStream();
		try {
			ObjectOutputStream pathoout = new ObjectOutputStream(pathout);
			pathoout.writeInt(oramName);
			pathoout.writeInt(ServerOperationType.GET_PATH_STASH);
			snapIds.writeExternal(pathoout);
			pathoout.writeInt(oldPositions.size());
			for (Integer val: oldPositions.values()) {
				pathoout.writeInt(val);
			}
			pathoout.flush();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		byte[] answer = pathOramProxy.invokeUnordered(pathout.toByteArray());
		ByteArrayInputStream in2 = new ByteArrayInputStream(answer);
		ObjectInputStream ois2 = new ObjectInputStream(in2);
		//TODO: vamos ler o length, depois faz se o for com esse número e cada um vai ter uma stash
		// também vai haver igual numero de entradas para o path
		int numberOfSnaps = ois2.readInt();
		TreeMap<Short, Pair<Double, byte[]>> intermediateStash = new TreeMap<>();
		TreeMap<Short, Pair<Double, byte[]>> intermediatePath = new TreeMap<>();
		for (int i = 0; i < numberOfSnaps; i++) {
			double snapId = ois2.readDouble();
			ClientStash retrievedStash = new ClientStash();
			retrievedStash.readExternal(ois2);

			ClientStash path = new ClientStash();
			path.readExternal(ois2);

			List<Block> stashContents = retrievedStash.getBlocks().stream()
					.filter(Block::isNotDummy)
					.collect(Collectors.toList());
			mergeStashes(intermediateStash,new ClientStash(stashContents),snapId);

			List<Block> pathContents = path.getBlocks().stream()
					.filter(Block::isNotDummy)
					.collect(Collectors.toList());

			mergeStashes(intermediatePath,new ClientStash(pathContents),snapId);

		}
		intermediatePath.forEach((key,val) -> clientStash.putBlock(new Block(key.byteValue(),val.getRight())));
		intermediateStash.forEach((key,val) -> clientStash.putBlock(new Block(key.byteValue(),val.getRight())));
		byte[] data = clientStash.getBlock(a.byteValue());
		Integer oldPosition = oldPositions.get(oldPositions.lowerKey(Double.MAX_VALUE));
		if(op.equals(Operation.WRITE)) {
			clientStash.putBlock(new Block(a.byteValue(),newData));
			data=newData;
		}
		List<Block> stashValues = clientStash.getBlocks();
		Path newPath = new Path(tree_levels);
		for (int level = tree_levels-1; level >= 0; level--) {
			List<Integer> compatiblePaths = checkPaths(oldPosition, level,tree_levels);
			ClientStash tempClientStash = new ClientStash(stashValues.parallelStream()
					.filter(val -> compatiblePaths.contains(tempPositionMap.getPosition(val.getKey())))
					.limit(Bucket.MAX_SIZE)
					.collect(Collectors.toList()));
			tempClientStash.getBlocks().forEach(clientStash::remove);
			Bucket b = new Bucket();
			b.writeBucket(tempClientStash.getBlocks());

			newPath.put(level, b);
		}
		/*if (debugMode)
			debugPrintPositionMap(tempPositionMap);*/

		try {
			ByteArrayOutputStream evictout = new ByteArrayOutputStream();
			ObjectOutputStream evictoout = new ObjectOutputStream(evictout);
			evictoout.writeInt(oramName);
			evictoout.writeInt(ServerOperationType.EVICT);
			snapIds.writeExternal(evictoout);
			evictoout.writeInt(oldPosition);
			ByteArrayOutputStream encryptevictout = new ByteArrayOutputStream();
			ObjectOutputStream encryptevictoout = new ObjectOutputStream(encryptevictout);
			tempPositionMap.writeExternal(encryptevictoout);
			clientStash.writeExternal(encryptevictoout);
			newPath.writeExternal(encryptevictoout);
			encryptevictoout.flush();
			evictout.write(encryptor.encrypt(encryptevictout.toByteArray()));
			pathOramProxy.invokeOrdered(evictout.toByteArray());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return data;
	}

	/*private void mergePaths(TreeMap<Short, Pair<Double, Short>> intermediatePath, Bucket deserializedBucket, Double snapId) {
		Block[] elements = deserializedBucket.readBucket();
		elements.forEach((key, value) -> {
			if (!intermediatePath.containsKey(key) || intermediatePath.get(key).getKey() < snapId) {
				intermediatePath.put(key, Pair.of(snapId, value));
			}
		});
	}*/

	private void mergeStashes(TreeMap<Short, Pair<Double, byte[]>> intermediateStash, ClientStash deserializedStash, Double snapId) {
		deserializedStash.getBlocks().forEach(block -> {
			short key = (short) block.getKey();
			byte[] value = block.getValue();
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
