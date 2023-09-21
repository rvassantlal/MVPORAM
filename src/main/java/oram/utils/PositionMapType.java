package oram.utils;

public enum PositionMapType {
	FULL_POSITION_MAP,
	TRIPLE_POSITION_MAP;

	public final static PositionMapType[] values = values();

	public static PositionMapType getPositionMapType(int ordinal) {
		return values[ordinal];
	}
}
