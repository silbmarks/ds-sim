/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package raid;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;


public class RaidSimulator {
	private int diskCount;
	private double maxTime;
	private double scrubInterval;
	private EventGenerator scrub = null;
	
	public RaidSimulator(int diskCount, double maxTime, double scrubInterval){
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
		LinkedList<Integer> []latentDefect = new LinkedList[diskCount];
		for(int i=0; i<states.length; i++){
			states[i]=0;
			latentDefect[i]=new LinkedList<Integer>();
		}
		
		double scrubStart = -1;
		double scrubLength =-1;
		double lastScrubCheck = -1;
		
		for(Event e: events.values()){
			//System.out.println(e);

			if(scrubStart>0&&lastScrubCheck>0&&scrubLength>0){
				int startSector = (int)((lastScrubCheck-scrubStart)/scrubLength*65536.0);
				int endSector = (int)((e.time-scrubStart)/scrubLength*65536.0);
				for(int i=0; i<latentDefect.length;i++){
					Iterator<Integer> iter = latentDefect[i].iterator();
					while(iter.hasNext()){
						int sector = iter.next();
						if(sector>=startSector&&sector<endSector){
							iter.remove();
						}
					}
					if(latentDefect[i].size()==0&&states[i]==1)
						states[i]=0;
				}
				lastScrubCheck = e.time;
			}
			if(e.type==Event.EventType.LatentDefect){
				states[e.id]=1;
				latentDefect[e.id].add(e.sector);
			}
			else if(e.type==Event.EventType.OperationFailure){
				states[e.id]=2;
			}
			else if(e.type==Event.EventType.RestoreComplete){
				states[e.id]=0;
			}
			else if(e.type==Event.EventType.ScrubStart){
				scrubStart = e.time;
				scrubLength = e.scrubLength;
				lastScrubCheck = e.time;
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
		RaidSimulator s = new RaidSimulator(8, 87600, 12);
		int failedCount=0;
		for(int i=0; i<10000; i++){
			boolean success = s.simulate();
			if(!success)
				failedCount++;
		}
		System.out.println("failureCount="+failedCount+" scrubCount="+scrubCount);
	}
}
