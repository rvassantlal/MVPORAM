package oram.utils;

public enum ServerOperationType {
	DEBUG,
	CREATE_ORAM,
	GET_PM,
	GET_ORAM,
	GET_PS,
	EVICT,
	UPDATE_CONCURRENT_CLIENTS;

	public final static ServerOperationType[] values = values();

	public static ServerOperationType getOperation(int ordinal) {
		return values[ordinal];
	}
}
