package oram.client;

public interface ORAMServiceProxy {

	int getProcessId();

	byte[] getDebugSnapshot(byte[] request);

	byte[] getPathMaps(byte[] request);

	byte[] getStashesAndPaths(byte[] request);

	byte[] sendEvictionPayload(byte[] request);

	byte[] evict(byte[] request);
}
