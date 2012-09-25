/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.failure;
import simulator.*;

import java.util.HashMap;
import java.util.Random;

public class Uniform implements EventGenerator{

    double frequency;
    String name;
    Random r;
    public double generateNextEvent(double currentTime) {
        return currentTime+ (double)r.nextInt((int)frequency*1000)/1000.0;
    }

    public void init(String name, HashMap<String, String> parameters) {
        this.frequency = Double.parseDouble(parameters.get("lamda"));
        this.name = name;
        this.r=new Random();
    }

    public void reset(double currentTime) {	
    }

    public String getName(){
        return name;
    }

    public double getCurrentTime() {
        return 0;
    }
}
