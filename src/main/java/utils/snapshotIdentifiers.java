package utils;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

public class snapshotIdentifiers implements Externalizable {

    private List<Double> snapIds;
    private int size;

    public snapshotIdentifiers(int paralellPathNumber){
        snapIds = new ArrayList<>();
        this.size=paralellPathNumber;
    }

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        for (Double snapId : snapIds) {
            objectOutput.writeDouble(snapId);
        }
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException {
        for (int i = 0; i < size; i++) {
            snapIds.add(objectInput.readDouble());
        }
    }

    public Double getByIndex(int indexOf) {
        return snapIds.get(indexOf);
    }

    public void add(Double id) {
        snapIds.add(id);
    }

    public List<Double> getSnaps() {
        return snapIds;
    }

    public int size() {
        return snapIds.size();
    }
}
