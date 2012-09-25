/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package raid;

import java.util.Random;

public class WeibullGenerator implements EventGenerator{
	private static Random rand = new Random(System.currentTimeMillis());
	
	private double gamma;
	private double lamda;
	private double beta;
	private double startTime;
	
	public WeibullGenerator(double startTime, double gamma, double lamda, double beta){
		this.startTime = startTime;
		this.gamma = gamma;
		this.lamda = lamda;
		this.beta = beta;
	}
	
	public void reset(double currentTime){
		this.startTime = currentTime;
	}
	
	private double F(double currentTime){
		//System.out.println(currentTime+" "+(1-Math.exp(-Math.pow((currentTime/lamda), beta))));
		return 1-Math.exp(-Math.pow((currentTime/lamda), beta));
	}
	
	public double nextEventTime(double currentTime){
		currentTime-=startTime;
		double r = rand.nextDouble();
		double R=(1-F(currentTime))*r+F(currentTime);
		double result = lamda*(Math.pow(-Math.log(1.0-R), 1/beta))+gamma+startTime;
		//if(new Double(result).isInfinite()){
		//	System.out.println("currentTime="+currentTime+" r="+r+" R="+R+" F="+F(currentTime));
		//}
		return lamda*(Math.pow(-Math.log(1.0-R), 1/beta))+gamma+startTime;
	}

}
