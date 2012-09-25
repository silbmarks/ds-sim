/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.unit;
import simulator.*;
import java.util.ArrayList;

public class SliceSet extends Unit {

    public ArrayList<Integer> slices;
    double originalFailureTime=0;

    public double getOriginalFailureTime() {
        return originalFailureTime;
    }

    public void setOriginalFailureTime(double originalFailureTime) {
        this.originalFailureTime = originalFailureTime;
    }

    public void init(String name, ArrayList<Integer> sliceSet) {
        super.init(name,null,null);
        slices = sliceSet;
    }
}
