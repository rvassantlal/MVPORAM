package utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

public class snapshotIdentifiers implements Externalizable {

    private List<Double> snapIds;

    public snapshotIdentifiers(){
        snapIds = new ArrayList<>();
    }

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {

    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {

    }

    public Double getByIndex(int indexOf) {
        return snapIds.get(indexOf);
    }

    public int size() {
        return snapIds.size();
    }
}
