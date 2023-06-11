package oram.utils;

public enum Status {
	SUCCESS,
	FAILED;

	public final static Status[] values = values();

	public static Status getStatus(int ordinal) {
		return values[ordinal];
	}
}
