/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.failure;
import simulator.*;

import java.util.HashMap;
import java.util.Random;

public class NoFailure implements EventGenerator{

    public double generateNextEvent(double currentTime) {
        return Double.MAX_VALUE;
    }

    public void init(String name, HashMap<String, String> parameters) {	
    }

    public void reset(double currentTime) {	
    }

    public String getName(){
        return "NoFailure";
    }

    public double getCurrentTime(){
        return Double.MAX_VALUE;
    }
}
