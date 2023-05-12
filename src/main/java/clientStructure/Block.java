package clientStructure;

public class Block<T> {
    private final byte key; //256 possibilities aren't a bit low?
    private final T value;

    public Block(byte k, T v){
        key=k;
        value=v;
    }
    public byte getKey() {
        return key;
    }

    public T getValue() {
        return value;
    }
}
