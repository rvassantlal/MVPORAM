package pathoram;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;


import org.apache.commons.lang3.SerializationUtils;

import structure.Bucket;
import structure.Operation;

public class Client {
	private static final Integer TREE_SIZE = 7;
	private static final Integer TREE_LEVELS = (int)(Math.log(TREE_SIZE+1) / Math.log(2));
	private static Server s;
	private static Random r = new Random();
	public static void main(String[] args) {
		s=new Server(TREE_SIZE);
		
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
		TreeMap<Short,Integer> positionMap = SerializationUtils.deserialize(s.getPositionMap());
		Integer oldPosition = positionMap.get(a);
		if(oldPosition==null)
			oldPosition=r.nextInt(TREE_SIZE/2+1);
		positionMap.put(a, r.nextInt(TREE_SIZE/2+1));
		TreeMap<Short, Short> stash=SerializationUtils.deserialize(s.getStash());
		List<Bucket> path = new ArrayList<>(1);
		if(oldPosition!=null) {
			path = s.getData(oldPosition).stream()
					.map(b -> b==null ? null : (Bucket)SerializationUtils.deserialize(b))
					.toList();
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
			
			newPath.put(l, SerializationUtils.serialize(b));
		}
		System.out.println("positionMap"+positionMap.entrySet().stream()
				.map(bvalue-> bvalue.getKey()+";"+bvalue.getValue())
				.collect(Collectors.toList()));
		s.doEviction(SerializationUtils.serialize(positionMap),
				SerializationUtils.serialize(stash), oldPosition,
				newPath);
		List<Bucket> tree = s.getTree().stream()
				.map(b -> b==null ? null : (Bucket)SerializationUtils.deserialize(b))
				.toList();
		System.out.println("TREE");
		for (Bucket bucket : tree) {
			if(bucket!=null) {
				System.out.println(bucket.readBucket().entrySet().stream()
						.map(bvalue-> bvalue.getKey()+";"+bvalue.getValue())
						.collect(Collectors.toList()));
			}else {
				System.out.println("null");
			}
				
			
		}
		/*s.getTree().stream()
				.map(b -> v.addAll(((Bucket)SerializationUtils.deserialize(b)).readBucket().entrySet()));
		System.out.println("Tree"+v.stream()
				.map(bvalue-> bvalue.getKey()+";"+bvalue.getValue())
				.collect(Collectors.toList()));*/
		return data;
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
