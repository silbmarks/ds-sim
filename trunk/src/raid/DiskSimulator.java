/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package raid;

import java.util.LinkedList;
import java.util.Random;
import java.util.TreeMap;

import raid.Event.EventType;

public class DiskSimulator {
	private double maxTime;
	private EventGenerator operationFailure = null;
	private EventGenerator restore = null;
	private EventGenerator latentDefect = null;
	private static Random rand = new Random();
	
	public DiskSimulator(double maxTime){
		this.maxTime = maxTime;
		operationFailure = new WeibullGenerator(0.0, 0.0, 461386.0, 1.12);
		restore = new WeibullGenerator(0.0, 6.0, 12.0, 2.0);
		latentDefect = new WeibullGenerator(0.0, 0.0, 9259.0, 1.0);
		
	}

	public TreeMap<Double, Event> generateEvents(){
		TreeMap<Double, Event> ret = new TreeMap<Double, Event>();
		double currentTime=0.0;
		//Generate operation failure and restore first
		while(true){
			double failure = operationFailure.nextEventTime(currentTime);
			if(failure>maxTime)
				break;
			currentTime = failure;
			ret.put(failure, new Event(Event.EventType.OperationFailure, currentTime));
			restore.reset(currentTime);
			double repair = restore.nextEventTime(currentTime);
			if(repair>maxTime)
				break;
			currentTime = repair;
			ret.put(repair, new Event(Event.EventType.RestoreComplete, currentTime));
			operationFailure.reset(currentTime);
		}
		ret.put(0.0, new Event(Event.EventType.Start, 0));
		ret.put(maxTime, new Event(Event.EventType.End, maxTime));
		TreeMap<Double, Event> tmp = new TreeMap<Double, Event>();
		
		for(Event e1: ret.values()){
			if(ret.higherKey(e1.time)==null)
				break;
			Event e2 = ret.higherEntry(e1.time).getValue();
			if(e1.type==Event.EventType.OperationFailure)
				continue;
			
			currentTime = e1.time;
			latentDefect.reset(currentTime);
			while(true){
				double latent = latentDefect.nextEventTime(currentTime);
				//System.out.println("latent="+latent);
				if(latent<e2.time){
					currentTime = latent;
					Event e = new Event(EventType.LatentDefect, currentTime);
					e.sector = rand.nextInt(65536);
					tmp.put(currentTime, e);
				}
				else
					break;
			}
			
		}
		ret.putAll(tmp);
		
		//Generate latent defect
		
		/*if(!failed){
			double failure = operationFailure.nextEventTime(currentTime);
			double latent = latentDefect.nextEventTime(currentTime);
			//System.out.println(failure+" "+latent);
			if(latent<failure && latent<maxTime){
				currentTime = latent;
				return new Event(Event.EventType.LatentDefect, currentTime);
			}
			else if(failure<=latent && failure<maxTime){
				currentTime = failure;
				failed = true;
				return new Event(Event.EventType.OperationFailure, currentTime);
			}
		}
		else{
			restore.reset(currentTime);
			double repair = restore.nextEventTime(currentTime);
			if(repair<maxTime){
				failed = false;
				currentTime += repair;
				operationFailure.reset(currentTime);
				latentDefect.reset(currentTime);
				return new Event(Event.EventType.RestoreComplete, currentTime);
			}
		}*/
		return ret;
	}
	
	public static void main(String[]args) throws Exception{
		DiskSimulator disk = new DiskSimulator(87600);
		TreeMap<Double, Event> events = disk.generateEvents();
		for(Double key:events.keySet()){
			System.out.println(events.get(key));
		}
	}

}
