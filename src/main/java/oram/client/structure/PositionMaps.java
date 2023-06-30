package oram.client.structure;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class PositionMaps implements Externalizable {
    private double newVersionId;
    private double[] outstandingVersionIds;
    private PositionMap[] positionMaps;

    public PositionMaps() {
    }

    public PositionMaps(double newVersionId, double[] outstandingVersionIds,
                        PositionMap[] positionMaps) {
        this.newVersionId = newVersionId;
        this.outstandingVersionIds = outstandingVersionIds;
        this.positionMaps = positionMaps;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeDouble(newVersionId);
        out.writeInt(outstandingVersionIds.length);
        for (double outstandingVersionId : outstandingVersionIds) {
            out.writeDouble(outstandingVersionId);
        }
        for (PositionMap positionMap : positionMaps) {
            positionMap.writeExternal(out);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException {
        newVersionId = in.readDouble();
        int size = in.readInt();
        outstandingVersionIds = new double[size];
        positionMaps = new PositionMap[size];
        for (int i = 0; i < size; i++) {
            outstandingVersionIds[i] = in.readDouble();
        }
        for (int i = 0; i < size; i++) {
            PositionMap e = new PositionMap();
            e.readExternal(in);
            positionMaps[i] = e;
        }
    }

    public double[] getOutstandingVersionIds() {
        return outstandingVersionIds;
    }

    public PositionMap[] getPositionMaps() {
        return positionMaps;
    }

    public double getNewVersionId() {
        return newVersionId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PositionMaps{ ");
        for (int i = 0; i < positionMaps.length; i++) {
            sb.append(outstandingVersionIds[i]).append(" : [").append(positionMaps[i].toString()).append("] ");
        }
        sb.append("} ");
        return sb.toString();

    }
}
