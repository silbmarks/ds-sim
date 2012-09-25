/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package raid;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;


public class RaidSimulator2 {
	private int diskCount;
	private double maxTime;
	private double scrubInterval;
	private EventGenerator scrub = null;
	
	public RaidSimulator2(int diskCount, double maxTime, double scrubInterval){
		this.diskCount = diskCount;
		this.maxTime = maxTime;
		this.scrubInterval = scrubInterval;
		this.scrub = new WeibullGenerator(0.0, 6.0, scrubInterval, 3.0);
		
	}
	public static int scrubCount=0;
	public boolean simulate(){
		//Generate disk events
		TreeMap<Double, Event> events = new TreeMap<Double, Event>();
		for(int i=0; i<diskCount; i++){
			DiskSimulator disk = new DiskSimulator(maxTime);
			TreeMap<Double, Event> tmp = disk.generateEvents();
			for(Event e:tmp.values()){
				e.id=i;
			}
			events.putAll(tmp);
		}
		//Generate scrub events
		double currentTime=0;
		while(true){
			double scrubStartTime = currentTime+0.1;
			if(scrubStartTime>maxTime)
				break;
			currentTime=scrubStartTime;
			Event scrubStart = new Event(Event.EventType.ScrubStart, currentTime);
			events.put(currentTime, scrubStart);
			
			scrub.reset(currentTime);
			double scrubTime = scrub.nextEventTime(currentTime);
			scrubStart.scrubLength = scrubTime-currentTime;
			if(scrubTime>maxTime)
				break;
			//System.out.println("scrub time="+(scrubTime));
			currentTime=scrubTime;
			scrubCount++;
			events.put(currentTime, new Event(Event.EventType.ScrubComplete, currentTime));
		}
		

		//Simulate
		int []states = new int[diskCount]; //0 normal, 1 latent defect, 2 op failure
		int []latentDefect = new int[diskCount];
		int []latentDefectTmp = new int[diskCount];
		for(int i=0; i<states.length; i++){
			states[i]=0;
			latentDefect[i]=0;
		}
		
		
		for(Event e: events.values()){
			//System.out.println(e);

			if(e.type==Event.EventType.LatentDefect){
				states[e.id]=1;
				latentDefect[e.id]++;
			}
			else if(e.type==Event.EventType.OperationFailure){
				states[e.id]=2;
			}
			else if(e.type==Event.EventType.RestoreComplete){
				states[e.id]=0;
			}
			else if(e.type==Event.EventType.ScrubStart){
				System.arraycopy(latentDefect, 0, latentDefectTmp, 0, diskCount);
			}
			else if(e.type == Event.EventType.ScrubComplete){
				for(int i=0; i<diskCount; i++){
					if(states[i]==1){
						latentDefect[i]-=latentDefectTmp[i];
						if(latentDefect[i]<0)
							throw new RuntimeException("wrong");
						else if(latentDefect[i]==0)
							states[i]=0;
					}
				}
			}
			
			if(hasFailed(states))
				return false;
		}
		return true;
	}
	
	private boolean hasFailed(int []state){
		int latentCount=0;
		int failedCount=0;
		for(int i=0; i<state.length; i++){
			if(state[i]==1)
				latentCount++;
			if(state[i]==2)
				failedCount++;
		}
		//System.out.println("failedCount="+failedCount+" latentCount="+latentCount);
		if(failedCount>1||(failedCount==1&&latentCount>=1))
			return true;
		else
			return false;
	}
	
	public static void main(String []args) throws Exception{
		RaidSimulator2 s = new RaidSimulator2(8, 87600, 12);
		int failedCount=0;
		for(int i=0; i<10000; i++){
			boolean success = s.simulate();
			if(!success)
				failedCount++;
		}
		System.out.println("failureCount="+failedCount+" scrubCount="+scrubCount);
	}
}
