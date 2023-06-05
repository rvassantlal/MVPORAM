package utils;

public enum Operation {
	CREATE_ORAM,
	GET_POSITION_MAP,
	READ,
	WRITE,
	GET_ORAM,
	GET_STASH_AND_PATH;

	public final static Operation[] values = values();

	public static Operation getOperation(int ordinal) {
		return values[ordinal];
	}
}
