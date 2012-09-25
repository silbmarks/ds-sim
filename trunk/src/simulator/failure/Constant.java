/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.failure;
import simulator.*;

import java.util.HashMap;
import java.util.Random;

public class Constant implements EventGenerator{

    double frequency;
    String name;
    double previousEvent=0;
    public double generateNextEvent(double currentTime) {
        if (previousEvent + frequency<=currentTime) {
            previousEvent=currentTime;
            return previousEvent;
        }
        return previousEvent+frequency; 
    }

    public void init(String name, HashMap<String, String> parameters) {
        this.frequency = Double.parseDouble(parameters.get("freq"));
        this.name = name;
    }


    public void reset(double currentTime) {	
        previousEvent=currentTime;
    }

    public String getName(){
        return name;
    }

    public double getCurrentTime() {
        return previousEvent;
    }
}
