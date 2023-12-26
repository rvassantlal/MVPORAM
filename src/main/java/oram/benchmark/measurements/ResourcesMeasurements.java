package oram.benchmark.measurements;

import worker.IProcessingResult;

public class ResourcesMeasurements implements IProcessingResult {
	private final long[] cpu;
	private final long[] memory;
	private final long[][] netReceived;
	private final long[][] netTransmitted;

	public ResourcesMeasurements(long[] cpu, long[] memory, long[][] netReceived, long[][] netTransmitted) {
		this.cpu = cpu;
		this.memory = memory;
		this.netReceived = netReceived;
		this.netTransmitted = netTransmitted;
	}

	public long[] getCpu() {
		return cpu;
	}

	public long[] getMemory() {
		return memory;
	}

	public long[][] getNetReceived() {
		return netReceived;
	}

	public long[][] getNetTransmitted() {
		return netTransmitted;
	}
}
