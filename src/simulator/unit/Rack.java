/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.unit;
import simulator.*;
import java.util.HashMap;

public class Rack extends Unit {
    private static boolean fastForward = false; // if true, rack failure and recovery events will be generated but ignored (neither handled nor written to file)

    public void init(String name, Unit parent, HashMap<String, String> parameters){
        super.init(name, parent, parameters);
        this.fastForward = Boolean.parseBoolean(parameters.get("fastForward"));
    }

    @Override
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
                if(fastForward) 
                    failEvent.ignore = true;

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
                if(fastForward) {
                    resultEvents.addEvent(new Event(Event.EventType.Recovered, currentTime, this, true));
                } else {
                    resultEvents.addEvent(new Event(Event.EventType.Recovered, currentTime, this));
                }
                lastRecoverTime = currentTime;
            }
        }
}
