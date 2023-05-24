package utils;

public class Utils {
	public static int toNumber(byte[] numberInBytes) {
		int number = Byte.toUnsignedInt(numberInBytes[0]);
		number <<= 8;
		number |= Byte.toUnsignedInt(numberInBytes[1]);
		number <<= 8;
		number |= Byte.toUnsignedInt(numberInBytes[2]);
		number <<= 8;
		number |= Byte.toUnsignedInt(numberInBytes[3]);
		return number;
	}

	public static byte[] toBytes(int number) {
		byte[] result = new byte[4];
		result[3] = (byte) number;
		number >>= 8;
		result[2] = (byte) number;
		number >>= 8;
		result[1] = (byte) number;
		number >>= 8;
		result[0] = (byte) number;

		return result;
	}
}
