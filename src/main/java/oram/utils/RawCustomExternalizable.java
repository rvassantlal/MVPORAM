package oram.utils;

public interface RawCustomExternalizable {

	/**
	 * Serializes the object into the output byte array.
	 * @param output Output byte array
	 * @param startOffset Start offset in the output byte array
	 * @return The number of bytes written into the output byte array
	 */
	int writeExternal(byte[] output, int startOffset);

	/**
	 * Deserializes the object from the input byte array.
	 * @param input Input byte array
	 * @param startOffset Start offset in the input byte array
	 * @return The number of bytes read from the input byte array
	 */
	int readExternal(byte[] input, int startOffset);
}
