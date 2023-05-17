package utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

public class EncryptedContents implements Externalizable {
    private List<byte[]> contents;
    private int size;
    public EncryptedContents(){contents=new ArrayList<>();}

    public EncryptedContents(int size) {
        contents=new ArrayList<>();
        this.size=size;
    }

    public void add(byte[] elem) {
        contents.add(elem);
    }

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {

    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {

    }
}
