package utils;

public enum Operation {
	CREATE_ORAM,
	READ,
	WRITE;

	public final static Operation[] values = values();

	public static Operation getOperation(int ordinal) {
		return values[ordinal];
	}
}
