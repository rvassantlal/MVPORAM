package oram.utils;

public enum ServerOperationType {
	DEBUG,
	CREATE_ORAM,
	GET_POSITION_MAP,
	GET_ORAM,
	GET_STASH_AND_PATH,
	EVICTION,
	UPDATE_CONCURRENT_CLIENTS;

	public final static ServerOperationType[] values = values();

	public static ServerOperationType getOperation(int ordinal) {
		return values[ordinal];
	}
}
