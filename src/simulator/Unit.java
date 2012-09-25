/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public abstract class Unit {
    protected LinkedList<Unit> children = new LinkedList<Unit>();
    protected Unit parent;
    protected String name;
    protected EventGenerator failureGenerator;
    protected EventGenerator recoveryGenerator;
    protected Metadata meta = new Metadata();

    private static long unitCount = 0;
    protected long id = 0;

    protected double lastFailureTime=0;
    public void setLastFailureTime(double ts){
        lastFailureTime=ts;
    }
    public double getLastFailureTime(){
        return lastFailureTime;
    }

    protected double lastBandwidthNeed=0;
    public void setLastBandwidthNeed(double bw) {
        lastBandwidthNeed = bw;
    }
    public double getLastBandwidthNeed() {
        return lastBandwidthNeed;
    }

    public void addChild(Unit unit){
        this.children.add(unit);
    }

    public List<Unit> getChildren(){
        return children;
    }

    public Unit getParent(){
        return parent;
    }

    public void init(String name, Unit parent, HashMap<String, String> parameters){
        this.name = name;
        this.parent = parent;
        id = unitCount;
        unitCount++;
    }

    public void addEventGenerator(EventGenerator generator){
        if(generator.getName().equals("failureGenerator"))
            this.failureGenerator = generator;
        else if (generator.getName().equals("recoveryGenerator"))
            this.recoveryGenerator = generator;
        else
            throw new RuntimeException("Unknown generator "+generator.getName());
    }

    public Metadata getMetadata(){
        return meta;
    }

    public void generateEvents(EventQueue resultEvents, double startTime, double endTime, boolean reset){
        double currentTime = startTime;
        double lastRecoverTime = startTime;
        if(failureGenerator==null){
            for(Unit u:children){
                u.generateEvents(resultEvents, startTime, endTime, true);
            }
            return;
        }
        while(true){
            if (reset) failureGenerator.reset(currentTime);

            double failureTime = failureGenerator.generateNextEvent(currentTime);
            currentTime = failureTime;
            if(currentTime>endTime){
                for(Unit u:children){
                    u.generateEvents(resultEvents, lastRecoverTime, endTime, true);
                }
                break;
            }
            Event failEvent = new Event(Event.EventType.Failure, currentTime, this);
            resultEvents.addEvent(failEvent);
            //System.out.println(this+" fails at "+currentTime);
            for(Unit u:children){
                u.generateEvents(resultEvents, lastRecoverTime, currentTime, true);
            }
            recoveryGenerator.reset(currentTime);
            double recoveryTime = recoveryGenerator.generateNextEvent(currentTime);
            assert (recoveryTime>failureTime);
            currentTime = recoveryTime;
            failEvent.nextRecoveryTime = recoveryTime;

            if(currentTime>endTime)
                break;

            resultEvents.addEvent(new Event(Event.EventType.Recovered, currentTime, this));
            lastRecoverTime = currentTime;
        }
    }

    @Override
        public String toString(){
            if(parent==null){
                return name;
            }
            else
                return parent.toString()+"."+name;
        }

    public void printAll(){
        System.out.println(this);
        for(Unit u:this.children){
            u.printAll();
        }
    }

    public long getID(){
        return id;
    }
}
