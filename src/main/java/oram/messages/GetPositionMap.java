package oram.messages;

import oram.utils.ORAMUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class GetPositionMap extends ORAMMessage {
	private int lastVersion;
	private Set<Integer> missingTriples;
	private int lastEvictionSequenceNumber;

	public GetPositionMap() {}

	public GetPositionMap(int oramId, int lastVersion, Set<Integer> missingTriples, int lastEvictionSequenceNumber) {
		super(oramId);
		this.lastVersion = lastVersion;
		this.missingTriples = missingTriples;
		this.lastEvictionSequenceNumber = lastEvictionSequenceNumber;
	}

	public int getLastVersion() {
		return lastVersion;
	}

	public Set<Integer> getMissingTriples() {
		return missingTriples;
	}

	@Override
	public int writeExternal(byte[] output, int startOffset) {
		int offset = super.writeExternal(output, startOffset);

		byte[] lastVersionBytes = ORAMUtils.toBytes(lastVersion);
		System.arraycopy(lastVersionBytes, 0, output, offset, 4);
		offset += 4;

		byte[] missingTriplesSizeBytes = ORAMUtils.toBytes(missingTriples.size());
		System.arraycopy(missingTriplesSizeBytes, 0, output, offset, 4);
		offset += 4;

		int[] missingTriplesArray = new int[missingTriples.size()];
		int k = 0;
		for (Integer missingTriple : missingTriples) {
			missingTriplesArray[k++] = missingTriple;
		}
		Arrays.sort(missingTriplesArray);
		for (int missingTriple : missingTriplesArray) {
			byte[] missingTripleBytes = ORAMUtils.toBytes(missingTriple);
			System.arraycopy(missingTripleBytes, 0, output, offset, 4);
			offset += 4;
		}

		byte[] lastEvictionVersionBytes = ORAMUtils.toBytes(lastEvictionSequenceNumber);
		System.arraycopy(lastEvictionVersionBytes, 0, output, offset, 4);
		offset += 4;

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = super.readExternal(input, startOffset);

		byte[] lastVersionBytes = new byte[4];
		System.arraycopy(input, offset, lastVersionBytes, 0, 4);
		lastVersion = ORAMUtils.toNumber(lastVersionBytes);
		offset += 4;

		byte[] missingTriplesSizeBytes = new byte[4];
		System.arraycopy(input, offset, missingTriplesSizeBytes, 0, 4);
		offset += 4;
		int missingTriplesSize = ORAMUtils.toNumber(missingTriplesSizeBytes);

		missingTriples = new HashSet<>(missingTriplesSize);
		for (int i = 0; i < missingTriplesSize; i++) {
			byte[] missingTripleBytes = new byte[4];
			System.arraycopy(input, offset, missingTripleBytes, 0, 4);
			int missingTriple = ORAMUtils.toNumber(missingTripleBytes);
			missingTriples.add(missingTriple);
			offset += 4;
		}

		byte[] lastEvictionVersionBytes = new byte[4];
		System.arraycopy(input, offset, lastEvictionVersionBytes, 0, 4);
		lastEvictionSequenceNumber = ORAMUtils.toNumber(lastEvictionVersionBytes);
		offset += 4;

		return offset;
	}

	@Override
	public int getSerializedSize() {
		return super.getSerializedSize() + 4 + 4 + missingTriples.size() * 4 + 4;
	}

	public int getLastEvictionSequenceNumber() {
		return lastEvictionSequenceNumber;
	}
}
