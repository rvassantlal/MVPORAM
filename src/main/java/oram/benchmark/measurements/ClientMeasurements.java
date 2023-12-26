package oram.benchmark.measurements;

import worker.IProcessingResult;

public class ClientMeasurements implements IProcessingResult {
	private final long[] globalLatency;
	private final long[] getPMLatency;
	private final long[] getPSLatency;
	private final long[] evictionLatency;
	private final long[] receivedPMSize;
	private final long[] receivedStashes;
	private final long[] receivedStashBlocks;
	private final long[] sentStashBlocks;
	private final long[] receivedPathSize;
	private final long[] sentPathSize;

	public ClientMeasurements(long[] globalLatency, long[] getPMLatency, long[] getPSLatency, long[] evictionLatency,
							  long[] receivedPMSize, long[] receivedStashes, long[] receivedStashBlocks,
							  long[] sentStashBlocks, long[] receivedPathSize, long[] sentPathSize) {

		this.globalLatency = globalLatency;
		this.getPMLatency = getPMLatency;
		this.getPSLatency = getPSLatency;
		this.evictionLatency = evictionLatency;
		this.receivedPMSize = receivedPMSize;
		this.receivedStashes = receivedStashes;
		this.receivedStashBlocks = receivedStashBlocks;
		this.sentStashBlocks = sentStashBlocks;
		this.receivedPathSize = receivedPathSize;
		this.sentPathSize = sentPathSize;
	}

	public long[] getGlobalLatency() {
		return globalLatency;
	}

	public long[] getGetPMLatency() {
		return getPMLatency;
	}

	public long[] getGetPSLatency() {
		return getPSLatency;
	}

	public long[] getEvictionLatency() {
		return evictionLatency;
	}

	public long[] getReceivedPMSize() {
		return receivedPMSize;
	}

	public long[] getReceivedStashes() {
		return receivedStashes;
	}

	public long[] getReceivedStashBlocks() {
		return receivedStashBlocks;
	}

	public long[] getSentStashBlocks() {
		return sentStashBlocks;
	}

	public long[] getReceivedPathSize() {
		return receivedPathSize;
	}

	public long[] getSentPathSize() {
		return sentPathSize;
	}
}
