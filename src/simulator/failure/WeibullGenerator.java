/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.failure;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;
import simulator.*;

public class WeibullGenerator implements EventGenerator{
    private static Random rand = new Random();

    private double gamma;
    private double lamda;
    private double beta;
    private double startTime;
    private String name;

    public double getRate(){
        return lamda;
    }

    public void reset(double currentTime){
        this.startTime = currentTime;
    }

    private double F(double currentTime){
        return 1-Math.exp(-Math.pow((currentTime/lamda), beta));
    }

    public double generateNextEvent(double currentTime) {
        currentTime-=startTime;
        if (currentTime<0) throw new RuntimeException("Negative current time!");
        double r = rand.nextDouble();
        double R=(1-F(currentTime))*r+F(currentTime);
        double result = lamda*(Math.pow(-Math.log(1.0-R), 1/beta))+gamma+startTime;
        if(Double.isInfinite(result) ||  Double.isNaN(result) ){
            System.out.println("currentTime="+currentTime+" r="+r+" R="+R+" F="+F(currentTime));
            throw new RuntimeException("generated time is Inf or NAN");
        }
        if (result<0) throw new RuntimeException("generated time is Negative!");
        return result;
    }

    public void init(double gamma, double lambda, double beta){
        this.gamma=gamma;
        this.lamda=lambda;
        this.beta=beta;
        startTime=0;
    }

    public void init(String name, HashMap<String, String> parameters) {
        this.gamma = Double.parseDouble(parameters.get("gamma"));
        this.lamda = Double.parseDouble(parameters.get("lamda"));;
        this.beta = Double.parseDouble(parameters.get("beta"));;
        this.name = name;
    }

    public String getName(){
        return this.name;
    }

    public double getCurrentTime() {
        return this.startTime;
    }

    public static void main(String s[]){
        WeibullGenerator w=new WeibullGenerator();
        w.init(0.02,0.03,1);
        HashMap<Double, Integer> hist=new HashMap<Double, Integer>();
        for(int i=0;i<100000;i++){
            double nextEvent=w.generateNextEvent(0);
            nextEvent=((double)Math.round(nextEvent*10000))/10000.0;
            if (hist.containsKey(nextEvent)){
                int p=hist.get(nextEvent);
                hist.put(nextEvent,++p);
            }else{
                hist.put(nextEvent,1);
            }
        }

        for(Entry<Double,Integer> i:hist.entrySet()){
            System.out.println(i.getKey() +" "+i.getValue());
        }
    }
}
