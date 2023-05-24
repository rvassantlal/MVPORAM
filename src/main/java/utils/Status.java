package utils;

import confidential.demo.map.client.Operation;

public enum Status {
	SUCCESS,
	FAILED;

	public final static Status[] values = values();

	public static Status getStatus(int ordinal) {
		return values[ordinal];
	}
}
