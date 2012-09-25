/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.failure;
import simulator.*;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

public class Piecewise implements EventGenerator{

    static Random r=new Random();
    String name;
    double previousEvent=0;

    double values[];
    double intervals[];

    public Piecewise(double[]_intervals, double[] _values){
        intervals=_intervals.clone();
        values=_values.clone();
        for(int i=1;i<intervals.length;i++){
            intervals[i]+=intervals[i-1];
        }
    }

    public double generateNextEvent(double currentTime) {
        float index=r.nextFloat();
        float range=r.nextFloat();

        int i=0;
        for(i=0;i<values.length;i++){
            if (index>=intervals[i]&& index<intervals[i+1]) break;
        }
        assert(i<values.length);
        double nextEvent=values[i]+range*(values[i+1]-values[i]);

        if (previousEvent + nextEvent<=currentTime) {
            previousEvent=currentTime;
            return previousEvent;
        }
        return previousEvent+nextEvent; 
    }

    public void init(String name, HashMap<String, String> parameters) {
        this.name=name;
    }


    public void reset(double currentTime) {	
        previousEvent=currentTime;
    }

    public String getName(){
        return name;
    }

    @Override
        public double getCurrentTime() {
            // TODO Auto-generated method stub
            return 0;
        }

    public static void main(String[] v){

        Piecewise p=new GFSAvailability2();
        LinkedList<Float> s=new LinkedList<Float>();
        for(int i=0;i<10000;i++){
            s.add((float)p.generateNextEvent(0));
        }

        int range_array[]=new int[12];
        for(int i=0;i<range_array.length;i++){
            range_array[i]=0;
        }

        for( float f=s.pop();s.size()>0;f=s.pop()){
            if (f<0.16) { range_array[0]++; continue;}
            if (f<1.6) { range_array[1]++; continue;}
            if (f<16.6) { range_array[2]++; continue;}
            if (f<166) { range_array[3]++; continue;}
        }
        for(int i=0;i<7;i++){
            System.err.println(range_array[i]);
        }
    }
}


