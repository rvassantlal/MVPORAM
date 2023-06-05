package clientStructure;

import oram.client.structure.Block;
import oram.client.structure.Bucket;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;

public class Path implements Externalizable {
    private final Bucket[] pathContents;
    public Path(int treeLevels) {
        pathContents = new Bucket[treeLevels];
    }

    public void put(int level, Bucket bucket) {
        pathContents[level] = bucket;
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

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        for (Bucket pathContent : pathContents) {
            pathContent.writeExternal(objectOutput);
        }
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException {
        for (int i = 0; i < pathContents.length; i++) {
            Bucket b = new Bucket();
            b.readExternal(objectInput);
            pathContents[i] = b;
        }
    }
}
