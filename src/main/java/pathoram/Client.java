package pathoram;


import bftsmart.tom.ServiceProxy;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import security.EncryptionAbstraction;
import clientStructure.*;
import utils.*;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class Client {
	private Logger logger = LoggerFactory.getLogger(this.getClass());
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
		return SerializationUtils.deserialize(pathOramProxy.invokeUnordered(createRequest(reqArgs), null, (byte) 0));

	}
	public byte[] readMemory(Integer position) throws IOException, ClassNotFoundException {
		return access(Operation.READ,position,null);
	}
	public byte[] writeMemory(Integer position,byte[] content) throws IOException, ClassNotFoundException {
		return access(Operation.WRITE,position,content);
	}
	public byte[] access(Operation op, int a, byte[] newData) throws IOException, ClassNotFoundException {
		//debugPrintTree();
		byte[] rawPositionMaps = pathOramProxy.invokeUnordered(
				createRequest(Arrays.asList(oramName,ServerOperationType.GET_POSITION_MAP)), null, (byte) 0);

		ImmutableTriple<Integer,byte[],snapshotIdentifiers> pmRequestResult = readPMRequestResult(rawPositionMaps);
		int paralellPathNumber = pmRequestResult.left;
		byte[] encryptedData = pmRequestResult.middle;
		snapshotIdentifiers snapIds = pmRequestResult.right;
		List<ClientPositionMap> posMaps = decryptPositionMaps(encryptedData,paralellPathNumber);

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
			}
			currentpositionMapClient.putAll(map);
		}
		if((oldPositions.size()==0 && op==Operation.READ) || tree_size==-1) {
			return null;
		}
		int tree_levels = (int)(Math.log(tree_size+1) / Math.log(2));
		if(oldPositions.size()==0){
			oldPositions.put(0.0,r.nextInt(tree_size/2+1));
		}
		currentpositionMapClient.putInPosition(a, r.nextInt(tree_size/2+1));
		byte[] answer = makeGetPathStashRequest(snapIds,oldPositions);

		ImmutableTriple<ArrayList<List<Double>>,ArrayList<List<Integer>>,byte[]> pathStashResult = readPathStashRequestResult(answer, oldPositions.size());

		ArrayList<List<Double>> snapsByPath = pathStashResult.left;
		ArrayList<List<Integer>> stashSizes = pathStashResult.middle;
		byte[] encryptedData2 = pathStashResult.right;
		logger.debug("len:"+encryptedData2.length);
		if(encryptedData2.length>0) {
			ImmutablePair<TreeMap<Short, Pair<Double, byte[]>>, TreeMap<Short, Pair<Double, byte[]>>> decryptedPathAndStash =
					readEncryptedPathStash(encryptedData2, oldPositions.size(), tree_levels, snapsByPath, stashSizes);
			TreeMap<Short, Pair<Double, byte[]>> intermediateStash = decryptedPathAndStash.left;
			TreeMap<Short, Pair<Double, byte[]>> intermediatePath = decryptedPathAndStash.right;
			intermediatePath.forEach((key, val) -> clientStash.putBlock(new Block(key.byteValue(), val.getRight())));
			intermediateStash.forEach((key, val) -> clientStash.putBlock(new Block(key.byteValue(), val.getRight())));
		}
		byte[] data = clientStash.getBlock((byte)a);
		Integer oldPosition = oldPositions.get(oldPositions.lowerKey(Double.MAX_VALUE));
		if(op.equals(Operation.WRITE)) {
			clientStash.putBlock(new Block((byte)a,newData));
			data=newData;
		}
		List<Block> stashValues = clientStash.getBlocks();
		Path newPath = new Path(tree_levels);
		for (int level = tree_levels-1; level >= 0; level--) {
			List<Integer> compatiblePaths = checkPaths(oldPosition, level,tree_levels);
			ClientStash tempClientStash = new ClientStash(stashValues.parallelStream()
					.filter(val -> compatiblePaths.contains(currentpositionMapClient.getPosition(val.getKey())))
					.limit(Bucket.MAX_SIZE)
					.collect(Collectors.toList()));
			tempClientStash.getBlocks().forEach(clientStash::remove);
			Bucket b = new Bucket();
			if(tempClientStash.size()>0)
				b.writeBucket(tempClientStash.getBlocks());
			newPath.put(level, b);
		}
		//debugPrintPositionMap(currentpositionMapClient);
		makeEvictionRequest(snapIds,oldPosition, currentpositionMapClient,clientStash,newPath);
		return data;
	}

	private void makeEvictionRequest(snapshotIdentifiers snapIds, Integer oldPosition, ClientPositionMap tempPositionMap, ClientStash clientStash, Path newPath) {
		try
				(ByteArrayOutputStream evictout = new ByteArrayOutputStream();
				 ObjectOutputStream evictoout = new ObjectOutputStream(evictout)){
			evictoout.writeInt(oramName);
			evictoout.writeInt(ServerOperationType.EVICT);
			evictoout.writeInt(snapIds.size());
			snapIds.writeExternal(evictoout);
			evictoout.writeInt(oldPosition);
			evictoout.writeInt(clientStash.size());
			evictoout.flush();
			try(
					ByteArrayOutputStream encryptevictout = new ByteArrayOutputStream();
					ObjectOutputStream encryptevictoout = new ObjectOutputStream(encryptevictout)) {
				tempPositionMap.writeExternal(encryptevictoout);
				encryptevictoout.flush();
				byte[] pm = encryptor.encrypt(encryptevictout.toByteArray());
				evictoout.writeInt(pm.length);
				evictoout.write(pm);
				encryptevictoout.reset();
				encryptevictout.reset();
				clientStash.writeExternal(encryptevictoout);
				encryptevictoout.flush();
				byte[] stash = encryptor.encrypt(encryptevictout.toByteArray());
				evictoout.writeInt(stash.length);
				evictoout.write(stash);
			}
			Path encryptedPath = new Path(newPath.size());
			Bucket[] nonEncryptedBuckets = newPath.getBuckets();
			for (int i = 0; i < nonEncryptedBuckets.length; i++) {
				Block[] blocks = nonEncryptedBuckets[i].readBucket();
				List<Block> encryptedBlocks= new ArrayList<>();
				for (int j = 0; j < blocks.length; j++) {
					encryptedBlocks.add(new Block(blocks[i].getKey(), encryptor.encrypt(blocks[i].getValue())));
				}
				encryptedPath.put(i,new Bucket(encryptedBlocks));
			}
			encryptedPath.writeExternal(evictoout);
			evictoout.flush();
			pathOramProxy.invokeOrdered(evictout.toByteArray(), null, (byte) 0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private ImmutableTriple<ArrayList<List<Double>>, ArrayList<List<Integer>>,byte[]> readPathStashRequestResult(byte[] answer, int oldPositionsSize) throws IOException {
		ArrayList<List<Double>> snapsByPath = new ArrayList<>();
		ArrayList<List<Integer>> stashSizes =  new ArrayList<>();
		byte[] encryptedData2;
		try(
				ByteArrayInputStream in2 = new ByteArrayInputStream(answer);
				ObjectInputStream ois2 = new ObjectInputStream(in2)) {

			int[] numberOfSnaps = new int[oldPositionsSize];
			int cumulativeStashesSize=0;
			for (int i = 0; i < oldPositionsSize; i++) {
				numberOfSnaps[i] = ois2.readInt();
				ArrayList<Double> arr = new ArrayList<>();
				ArrayList<Integer> pathIdStashSizes = new ArrayList<>();
				for (int j = 0; j < numberOfSnaps[i]; j++) {
					arr.add(ois2.readDouble());
					int stashSize = ois2.readInt();
					pathIdStashSizes.add(stashSize);
					cumulativeStashesSize+=stashSize;
				}
				snapsByPath.add(arr);
				stashSizes.add(pathIdStashSizes);
			}
			int encryptedLength = cumulativeStashesSize+(Arrays.stream(numberOfSnaps).sum()*tree_size*Bucket.MAX_SIZE);
			encryptedData2 = new byte[encryptedLength];
			ois2.read(encryptedData2);
			encryptedData2 = encryptor.decrypt(encryptedData2);
		}
		return ImmutableTriple.of(snapsByPath,stashSizes,encryptedData2);
	}

	private ImmutablePair<TreeMap<Short, Pair<Double,byte[]>>, TreeMap<Short, Pair<Double,byte[]>>> readEncryptedPathStash(byte[] encryptedData, int oldPositionsSize, int tree_levels, ArrayList<List<Double>> snapsByPath, ArrayList<List<Integer>> stashSizes) throws IOException, ClassNotFoundException {
		TreeMap<Short, Pair<Double, byte[]>> intermediateStash = new TreeMap<>();
		TreeMap<Short, Pair<Double, byte[]>> intermediatePath = new TreeMap<>();
		try(
				ByteArrayInputStream encryptedin2 = new ByteArrayInputStream(encryptedData);
				ObjectInputStream encryptedois2 = new ObjectInputStream(encryptedin2)) {
			for (int i = 0; i < oldPositionsSize; i++) {
				for (Double snapId : snapsByPath.get(i)) {
					ClientStash retrievedStash = new ClientStash(stashSizes.get(i).get(snapsByPath.get(i).indexOf(snapId)));
					retrievedStash.readExternal(encryptedois2);
					Path path = new Path(tree_levels);
					path.readExternal(encryptedois2);
					List<Block> stashContents = retrievedStash.getBlocks().stream()
							.filter(Block::isNotDummy)
							.collect(Collectors.toList());
					mergeStashes(intermediateStash, new ClientStash(stashContents), snapId);

					List<Block> pathContents = path.getBlocks().stream()
							.filter(Block::isNotDummy)
							.collect(Collectors.toList());

					mergeStashes(intermediatePath, new ClientStash(pathContents), snapId);
				}
			}
		}
		return ImmutablePair.of(intermediateStash,intermediatePath);
	}

	private ImmutableTriple<Integer,byte[], snapshotIdentifiers> readPMRequestResult(byte[] rawPositionMaps) throws IOException {
		try(ByteArrayInputStream in = new ByteArrayInputStream(rawPositionMaps); //TODO:use try with resources
			ObjectInputStream ois = new ObjectInputStream(in)) {
			int paralellPathNumber = ois.readInt();
			snapshotIdentifiers snapIds = new snapshotIdentifiers(paralellPathNumber);
			snapIds.readExternal(ois);
			int encryptedLength = ois.readInt();
			byte[] encryptedData = new byte[encryptedLength];
			ois.read(encryptedData);
			return ImmutableTriple.of(paralellPathNumber,encryptedData,snapIds);
		}
	}

	private byte[] makeGetPathStashRequest(snapshotIdentifiers snapIds, TreeMap<Double, Integer> oldPositions) {
		try (ByteArrayOutputStream pathout = new ByteArrayOutputStream();
			 ObjectOutputStream pathoout = new ObjectOutputStream(pathout)){
			pathoout.writeInt(oramName);
			pathoout.writeInt(ServerOperationType.GET_PATH_STASH);
			pathoout.writeInt(snapIds.size());
			snapIds.writeExternal(pathoout);
			pathoout.writeInt(oldPositions.size());
			for (Integer val: oldPositions.values()) {
				pathoout.writeInt(val);
			}
			pathoout.flush();
			return pathOramProxy.invokeUnordered(pathout.toByteArray(), null, (byte) 0);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new byte[0];
	}

	private List<ClientPositionMap> decryptPositionMaps(byte[] encryptedData, int paralellPathNumber) throws IOException {
		if(encryptedData.length>0) {
			encryptedData = encryptor.decrypt(encryptedData);

			try (ByteArrayInputStream encryptedin = new ByteArrayInputStream(encryptedData);
				 ObjectInputStream encryptedois = new ObjectInputStream(encryptedin)) {
				List<ClientPositionMap> posMaps = new ArrayList<>();
				for (int i = 0; i < paralellPathNumber; i++) {
					ClientPositionMap p = new ClientPositionMap(tree_size);
					p.readExternal(encryptedois);
					posMaps.add(p);
				}
				return posMaps;
			}
		}
		return new ArrayList<>();
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


	private void debugPrintPositionMap(ClientPositionMap tempPositionMap) {
		logger.debug("positionMap"+tempPositionMap.entrySet().stream()
				.map(bvalue-> bvalue.getKey()+";"+bvalue.getValue())
				.collect(Collectors.toList()));

	}
	private String debugPrintTree() throws IOException, ClassNotFoundException {
		byte[] rawTree = pathOramProxy.invokeUnordered(
				createRequest(Arrays.asList(oramName,ServerOperationType.GET_TREE)), null, (byte) 0);
		if (rawTree!=null) {
			ByteArrayInputStream in = new ByteArrayInputStream(rawTree);
			ObjectInputStream ois = new ObjectInputStream(in);

			List<List<byte[]>> obj = ((List<List<byte[]>>) ois.readObject());
			obj.forEach(this::auxPrintTree);
		}
		return null;
	}
	private void auxPrintTree (List<byte[]> rawTree) {
		if(rawTree!=null) {
			List<String> tree = rawTree.stream()
					.map(b -> b==null ? null : Arrays.toString((encryptor.decrypt(b))))
					.collect(Collectors.toList());
			logger.debug(TreePrinter.print(tree));
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
