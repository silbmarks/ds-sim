/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator;

import java.util.HashMap;

public class Event {
    public enum EventType {
        Start, Failure, Recovered, EagerRecoveryStart, EagerRecoveryInstallment, LatentDefect, ScrubStart, ScrubComplete, End
    }

    private final EventType type;
    private final double time;
    private final Unit unit;
    private HashMap<String, Object> attributes;
    public boolean ignore = false;
    public int info = -100; // a field to send additional info
    public double nextRecoveryTime = 0; // to give failure events foreknowledge of recovery time

    public Event(EventType type, double time, Unit unit){
        this.type = type;
        this.time = time;
        this.unit = unit;
    }

    public Event(EventType type, double time, Unit unit, int info){
        this.type = type;
        this.time = time;
        this.unit = unit;
        this.info = info;
    }

    public Event(EventType type, double time, Unit unit, boolean ignore){
        this.type = type;
        this.time = time;
        this.unit = unit;
        this.ignore = ignore;
    }

    public EventType getType(){
        return type;
    }

    public double getTime(){
        return time;
    }

    public Unit getUnit(){
        return unit;
    }

    public Object getAttribute(String key){
        if(attributes==null)
            return null;
        return attributes.get(key);
    }

    public void setAttribute(String key, Object value){
        if(attributes==null)
            attributes = new HashMap<String, Object>();
        attributes.put(key, value);
    }

    @Override
        public String toString(){
            return time+" "+unit+" "+type+" "+info+" "+ignore;
        }
}
