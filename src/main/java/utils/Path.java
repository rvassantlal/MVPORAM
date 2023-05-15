package utils;

import clientStructure.Bucket;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class Path implements Externalizable {
    public Path(int tree_levels) {
        
    }

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {

    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {

    }

    public void put(int level, Bucket b) {
    }
}
