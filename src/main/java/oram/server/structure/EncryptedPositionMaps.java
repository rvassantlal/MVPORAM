package oram.server.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class EncryptedPositionMaps implements Externalizable {

    private double newVersionId;
    private double[] outstandingVersionIds;
    private EncryptedPositionMap[] encryptedPositionMaps;

    public EncryptedPositionMaps(){}

    public EncryptedPositionMaps(double newVersionId, double[] outstandingVersionIds,
                                 EncryptedPositionMap[] encryptedPositionMaps) {
        this.newVersionId = newVersionId;
        this.outstandingVersionIds = outstandingVersionIds;
        this.encryptedPositionMaps = encryptedPositionMaps;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeDouble(newVersionId);
        out.writeInt(outstandingVersionIds.length);
        for (double outstandingVersionId : outstandingVersionIds) {
            out.writeDouble(outstandingVersionId);
        }
        for (EncryptedPositionMap encryptedPositionMap : encryptedPositionMaps) {
            encryptedPositionMap.writeExternal(out);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        newVersionId = in.readDouble();
        int size = in.readInt();
        outstandingVersionIds = new double[size];
        encryptedPositionMaps = new EncryptedPositionMap[size];
        for (int i = 0; i < size; i++) {
            outstandingVersionIds[i] = in.readDouble();
        }
        for (int i = 0; i < size; i++) {
            EncryptedPositionMap e = new EncryptedPositionMap();
            e.readExternal(in);
            encryptedPositionMaps[i] = e;
        }
    }

    public double[] getOutstandingVersionIds() {
        return outstandingVersionIds;
    }

    public EncryptedPositionMap[] getEncryptedPositionMaps() {
        return encryptedPositionMaps;
    }

    public double getNewVersionId() {
        return newVersionId;
    }
}
