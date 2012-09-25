/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.unit;

import simulator.Event;
import simulator.EventGenerator;
import simulator.EventQueue;
import simulator.Unit;

public class Disk extends Unit {
    private EventGenerator latentErrorGenerator;

    @Override
        public void addEventGenerator(EventGenerator generator){
            if(generator.getName().equals("latentErrorGenerator"))
                this.latentErrorGenerator = generator;
            else 
                super.addEventGenerator(generator);
        }

    @Override
        public void generateEvents(EventQueue resultEvents, double startTime, double endTime, boolean reset){
            double currentTime = startTime;
            double lastRecoverTime = startTime;

            if(children!=null&&children.size()!=0)
                throw new RuntimeException("Disk should not have any children");

            while(true){
                failureGenerator.reset(currentTime);
                double failureTime = failureGenerator.generateNextEvent(currentTime);
                currentTime = failureTime;
                if(currentTime>endTime){
                    generateLatentErrors(resultEvents, lastRecoverTime, endTime);
                    break;
                }
                resultEvents.addEvent(new Event(Event.EventType.Failure, currentTime, this));
                //System.out.println(this+" fails at "+currentTime);
                generateLatentErrors(resultEvents, lastRecoverTime, currentTime);
                recoveryGenerator.reset(currentTime);
                double recoveryTime = recoveryGenerator.generateNextEvent(currentTime);
                assert (recoveryTime>failureTime);
                currentTime = recoveryTime;
                if(currentTime>endTime)
                    break;
                resultEvents.addEvent(new Event(Event.EventType.Recovered, currentTime, this));
                lastRecoverTime = currentTime;
            }
        }

    private void generateLatentErrors(EventQueue resultEvents, double startTime, double endTime){
        latentErrorGenerator.reset(startTime);
        double currentTime = startTime;
        while(true){
            double latentErrorTime = latentErrorGenerator.generateNextEvent(currentTime);
            currentTime = latentErrorTime;
            if(currentTime>endTime)
                break;
            resultEvents.addEvent(new Event(Event.EventType.LatentDefect, currentTime, this));
        }
    }
}
