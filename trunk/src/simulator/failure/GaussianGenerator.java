/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.failure;
import simulator.*;

import java.util.HashMap;
import java.util.Random;

import javax.management.RuntimeErrorException;

public class GaussianGenerator implements EventGenerator{

    private static Random rand = new Random();
    private double mean;
    private double stddev;
    private double startTime;
    private double minval;
    private String name;

    public void init(String name, HashMap<String, String> parameters) {
        this.mean = Double.parseDouble(parameters.get("mean"));
        this.stddev = Double.parseDouble(parameters.get("stddev"));
        this.minval=Double.parseDouble(parameters.get("minval"));
        if (mean<stddev) throw new RuntimeException("Mean is smaller than stddev -> results in negative generated times");
        if (minval>mean-stddev) throw new RuntimeException("Minval is too large and will slow down the simulation");
        this.startTime = 0;
        this.name = name;
    }

    public void reset(double currentTime) {	
        this.startTime=currentTime;
    }

    public double generateNextEvent(double currentTime) {
        if (currentTime<startTime) throw new RuntimeException("current time is smaller than the start time");
        double nextVal=0;
        while(nextVal<minval){
            nextVal=(rand.nextGaussian()*stddev + mean);
        }

        if (nextVal<0) throw new RuntimeException("Negative value generated!");
        if (startTime + nextVal<=currentTime) {
            startTime=currentTime;
            return startTime;
        }
        return startTime +nextVal; 
    }

    public String getName(){
        return name;
    }

    public double getCurrentTime() {
        return startTime;
    }
}
