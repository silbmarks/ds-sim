/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator;

import java.util.HashMap;

public interface EventGenerator {
    public void init(String name, HashMap<String, String> parameters);
    public double generateNextEvent(double currentTime);
    public void reset(double currentTime);
    public String getName();
    public double getCurrentTime();
}
