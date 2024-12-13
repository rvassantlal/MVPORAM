package oram.messages;

import oram.utils.ORAMUtils;

import java.util.HashSet;
import java.util.Set;

public class GetPathMaps extends ORAMMessage {
	private int lastVersion;
	private Set<Integer> missingTriples;

	public GetPathMaps() {}

	public GetPathMaps(int oramId, int lastVersion, Set<Integer> missingTriples) {
		super(oramId);
		this.lastVersion = lastVersion;
		this.missingTriples = missingTriples;
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

		ORAMUtils.serializeInteger(lastVersion, output, offset);
		offset += Integer.BYTES;

		ORAMUtils.serializeInteger(missingTriples.size(), output, offset);
		offset += Integer.BYTES;

		int[] missingTriplesArray = ORAMUtils.convertSetIntoOrderedArray(missingTriples);
		for (int missingTriple : missingTriplesArray) {
			ORAMUtils.serializeInteger(missingTriple, output, offset);
			offset += Integer.BYTES;
		}

		return offset;
	}

	@Override
	public int readExternal(byte[] input, int startOffset) {
		int offset = super.readExternal(input, startOffset);

		lastVersion = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		int missingTriplesSize = ORAMUtils.deserializeInteger(input, offset);
		offset += Integer.BYTES;

		missingTriples = new HashSet<>(missingTriplesSize);
		for (int i = 0; i < missingTriplesSize; i++) {
			int missingTriple = ORAMUtils.deserializeInteger(input, offset);
			offset += Integer.BYTES;

			missingTriples.add(missingTriple);
		}

		return offset;
	}

	@Override
	public int getSerializedSize() {
		return super.getSerializedSize() + Integer.BYTES * (2 + missingTriples.size());
	}
}
