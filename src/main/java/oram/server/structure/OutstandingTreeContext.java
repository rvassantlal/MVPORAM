package oram.server.structure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class OutstandingTreeContext {
    private final HashMap<Integer, Set<BucketSnapshot>> outstandingTree;
    private final Set<Integer> dirtyLocations;

    public OutstandingTreeContext(int treeSize) {
        this.outstandingTree = new HashMap<>(treeSize);
        this.dirtyLocations = new HashSet<>();
        for (int i = 0; i < treeSize; i++) {
            outstandingTree.put(i, new HashSet<>());
        }
    }

    public OutstandingTreeContext(OutstandingTreeContext outstandingTreeContext) {
        this.outstandingTree = new HashMap<>(outstandingTreeContext.outstandingTree);
        this.dirtyLocations = new HashSet<>(outstandingTreeContext.dirtyLocations);
    }

    public OutstandingTreeContext(int treeSize, Set<Integer> dirtyLocations) {
        this.outstandingTree = new HashMap<>(treeSize);
        this.dirtyLocations = new HashSet<>(dirtyLocations);
    }

    public Set<BucketSnapshot> getLocation(int pathLocation) {
        return outstandingTree.get(pathLocation);
    }

    public void markDirtyLocation(int dirtyLocation) {
        dirtyLocations.add(dirtyLocation);
    }

    public Set<Integer> getDirtyLocations() {
        return dirtyLocations;
    }

    public void updateLocation(Integer location, Set<BucketSnapshot> outstandingBuckets) {
        outstandingTree.put(location, outstandingBuckets);
    }

    public void clearDirtyLocations() {
        dirtyLocations.clear();
    }

    @Override
    public String toString() {
        return "OutstandingTreeContext{\n" +
                "\tdirtyLocations: " + dirtyLocations +
                "}";
    }
}
