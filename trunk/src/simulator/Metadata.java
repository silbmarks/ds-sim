/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator;

import java.util.HashSet;

public class Metadata {
    public UnitState state;
    public long lastFailureTime;
    public int []slices;
    public int sliceCount=0;
    public HashSet<Integer> defectiveSlices; // slices hit by latent error
    public HashSet<Integer> knownDefectiveSlices; // latent errors detected during scrubbing
    public HashSet<Integer> nonexistentSlices; // slices not yet recovered after disk failure
}
