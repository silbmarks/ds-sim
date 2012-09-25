/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.unit;

import java.util.HashMap;
import java.util.Map.Entry;

import simulator.Event;
import simulator.EventGenerator;
import simulator.EventQueue;
import simulator.Unit;

public class Raid extends Unit {
	private EventGenerator scrubGenerator;

	@Override
	public void addEventGenerator(EventGenerator generator) {
		if (generator.getName().equals("scrubGenerator"))
			this.scrubGenerator = generator;
	}

	@Override
	public void generateEvents(EventQueue resultEvents, double startTime,
			double endTime, boolean reset) {
		EventQueue internalEvents = new EventQueue();
		
		for(Unit u:children){
			if(!(u instanceof Disk))
				throw new RuntimeException("Child of Raid must be Disk");
			u.generateEvents(internalEvents, startTime, endTime, reset);
		}
		
		//generate scrub events
		double currentTime=startTime;
		while(true){
			double scrubStartTime = currentTime+0.1;
			if(scrubStartTime>endTime)
				break;
			currentTime=scrubStartTime;
			Event scrubStart = new Event(Event.EventType.ScrubStart, currentTime, this);
			if(currentTime<startTime||currentTime>endTime)
				throw new RuntimeException("wrong time "+currentTime);
			internalEvents.addEvent(scrubStart);
			
			scrubGenerator.reset(currentTime);
			double scrubTime = scrubGenerator.generateNextEvent(currentTime);
			if(scrubTime>endTime)
				break;
			//System.out.println("scrub time="+(scrubTime));
			currentTime=scrubTime;
			assert currentTime>=startTime&&currentTime<=endTime;
			internalEvents.addEvent(new Event(Event.EventType.ScrubComplete, currentTime, this));
		}
		
		HashMap<Unit, Integer> states = new HashMap<Unit, Integer>(); //0 normal, 1 latent defect, 2 op failure
		HashMap<Unit, Integer> latentDefect = new HashMap<Unit, Integer>();
		HashMap<Unit, Integer> latentDefectTmp = new HashMap<Unit, Integer>();	
		
		for(Unit u:children){
			states.put(u, 0);
			latentDefect.put(u, 0);
		}
		
		for(Event e: internalEvents.getAllEvents()){
			//System.out.println(e);

			if(e.getType()==Event.EventType.LatentDefect){
				states.put(e.getUnit(), 1);
				int latentDefectsNo = 1;
				if(latentDefect.containsKey(e.getUnit()))
					latentDefectsNo = latentDefect.get(e.getUnit())+1;
				latentDefect.put(e.getUnit(), latentDefectsNo);
			}
			else if(e.getType()==Event.EventType.Failure){
				states.put(e.getUnit(), 2);
			}
			else if(e.getType()==Event.EventType.Recovered){
				states.put(e.getUnit(), 0);
			}
			else if(e.getType()==Event.EventType.ScrubStart){
				latentDefectTmp = (HashMap<Unit, Integer>)latentDefect.clone();
			}
			else if(e.getType() == Event.EventType.ScrubComplete){
				for(Entry<Unit, Integer> entry: states.entrySet()){
					if(states.get(entry.getKey())==1){
						int newCount = latentDefect.get(entry.getKey())-latentDefectTmp.get(entry.getKey());
						latentDefect.put(entry.getKey(), newCount);
						
						if(newCount<0)
							throw new RuntimeException("wrong");
						else if(newCount==0)
							entry.setValue(0);
					}
				}
			}
			
			if(hasFailed(states)){
				//System.out.println("Raid failed "+this);
				resultEvents.addEvent(new Event(Event.EventType.Failure, e.getTime(), this));
				return;
			}
		}

	}
		
		private boolean hasFailed(HashMap<Unit, Integer> states){
			int latentCount=0;
			int failedCount=0;
			for(Integer state:states.values()){
				if(state==1)
					latentCount++;
				if(state==2)
					failedCount++;
			}
			//System.out.println("failedCount="+failedCount+" latentCount="+latentCount);
			if(failedCount>1||(failedCount==1&&latentCount>=1))
				return true;
			else
				return false;
		}

	
}
