package oram.benchmark.measurements;

import worker.IProcessingResult;

public class ServerMeasurements implements IProcessingResult {
	private final long[] clients;
	private final long[] delta;
	private final long[] nGetPMRequests;
	private final long[] nGetPSRequests;
	private final long[] nEvictionRequests;
	private final long[] getPMAvg;
	private final long[] getPSAvg;
	private final long[] evictionAvg;
	private final long[] outstanding;

	public ServerMeasurements(long[] clients, long[] delta, long[] nGetPMRequests, long[] nGetPSRequests,
							  long[] nEvictionRequests, long[] getPMAvg, long[] getPSAvg, long[] evictionAvg,
							  long[] outstanding) {

		this.clients = clients;
		this.delta = delta;
		this.nGetPMRequests = nGetPMRequests;
		this.nGetPSRequests = nGetPSRequests;
		this.nEvictionRequests = nEvictionRequests;
		this.getPMAvg = getPMAvg;
		this.getPSAvg = getPSAvg;
		this.evictionAvg = evictionAvg;
		this.outstanding = outstanding;
	}

	public long[] getClients() {
		return clients;
	}

	public long[] getDelta() {
		return delta;
	}

	public long[] getNGetPMRequests() {
		return nGetPMRequests;
	}

	public long[] getNGetPSRequests() {
		return nGetPSRequests;
	}

	public long[] getNEvictionRequests() {
		return nEvictionRequests;
	}

	public long[] getGetPMAvg() {
		return getPMAvg;
	}

	public long[] getGetPSAvg() {
		return getPSAvg;
	}

	public long[] getEvictionAvg() {
		return evictionAvg;
	}

	public long[] getOutstanding() {
		return outstanding;
	}
}
