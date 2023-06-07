package oram.client.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.Objects;

public class Block implements Externalizable {
    private int address;
    private byte[] content;

    public Block(int blockSize) {
        content = new byte[blockSize];
    }

    public Block(int blockSize, int address, byte[] newContent){
        this.address = address;
        this.content = new byte[blockSize];
        Arrays.fill(content, (byte) ' ');// TODO use other padding strategy
        System.arraycopy(newContent, 0, content, 0, newContent.length);
    }

    public int getAddress() {
        return address;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] newContent) {
        Arrays.fill(content, (byte) ' ');
        System.arraycopy(newContent, 0, content, 0, newContent.length);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(address);
        out.write(content);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        address = in.readInt();
        in.readFully(content);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Block block = (Block) o;
        return address == block.address && Arrays.equals(content, block.content);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(address);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }

    @Override
    public String toString() {
        return "Block{" + address +
                ", " + Arrays.toString(content) +
                '}';
    }
}
