package clientStructure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;

public class Path implements Externalizable {

    Bucket[] pathContents;
    public Path(int tree_levels) {
        pathContents= new Bucket[tree_levels];
        for (int i = 0; i < tree_levels; i++) {
            put(i,new Bucket());
        }
    }

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        for (Bucket pathContent : pathContents) {
            pathContent.writeExternal(objectOutput);
        }
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException {
        for (int i = 0; i < pathContents.length; i++) {
            Bucket b= new Bucket();
            b.readExternal(objectInput);
            pathContents[i] = b;
        }
    }

    public void put(int level, Bucket b) {
        pathContents[level]=b;
    }

    public ArrayList<Block> getBlocks() {
        ArrayList<Block> blocks = new ArrayList<>();
        for (Bucket b: pathContents) {
            blocks.addAll(Arrays.asList(b.readBucket()));
        }
        return blocks;
    }

    public Bucket[] getBuckets() {
        return pathContents;
    }

    public int size() {
        return pathContents.length;
    }

    //TODO:dummy this
}
