package clientStructure;

import java.util.Arrays;

public class Block {
    private final byte key; //256 possibilities aren't a bit low?
    private final byte[] value;

    public static final int standard_size=4096;

    public Block(byte k, byte[] v){
        key=k;
        value=v;
    }
    public byte getKey() {
        return key;
    }

    public byte[] getValue() {
        return value;
    }

    public boolean isNotDummy() {
        return !Arrays.equals(value, new byte[standard_size]);
    }
}
