package clientStructure;

import java.util.Arrays;

public class Block {
    private final byte key; //256 possibilities aren't a bit low?
    private final byte[] value;

    public static final int standard_size=512;

    public Block(byte k, byte[] v){
        key=k;
        value=v;
    }

    public Block(){
        key=0;
        value=new byte[standard_size];
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
