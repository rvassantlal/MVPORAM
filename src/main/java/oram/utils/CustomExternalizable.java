package oram.utils;

import java.io.*;

public interface CustomExternalizable {
	void writeExternal(DataOutput out) throws IOException;

	void readExternal(DataInput in) throws IOException;
}
