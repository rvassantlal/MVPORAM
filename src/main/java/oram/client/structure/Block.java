package oram.client.structure;

import oram.utils.ORAMUtils;

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

    public Block(int address, byte[] content){
        this.address = address;
        this.content = content;
    }

    public int getAddress() {
        return address;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public boolean isDummy() {
        return address == ORAMUtils.DUMMY_ADDRESS && Arrays.equals(content, ORAMUtils.DUMMY_BLOCK);
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
}
