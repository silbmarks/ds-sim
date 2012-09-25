/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package raid;

public class Event {
	public enum EventType {
		Start, OperationFailure, RestoreComplete, LatentDefect, ScrubStart, ScrubComplete, End
	}
	
	public final EventType type;
	public final double time;
	public int id;
	public int sector;
	public double scrubLength;
	
	public Event(EventType type, double time){
		this.type = type;
		this.time = time;
	}
	
	public String toString(){
		return type+" "+time+" id="+id;
	}
}
