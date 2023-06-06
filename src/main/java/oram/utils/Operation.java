package oram.utils;

public enum Operation {
	READ,
	WRITE;

	public final static Operation[] values = values();

	public static Operation getOperation(int ordinal) {
		return values[ordinal];
	}
}
