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

public class DiskWithScrubbing extends Disk {
    private EventGenerator latentErrorGenerator;
    private EventGenerator scrubGenerator;

    private double lastRecoveryTime=0;
    private double lastScrubStart;

    public void setLastScrubStart(double lastScrubStart) {
        this.lastScrubStart = lastScrubStart;
    }

    public double getLastScrubStart(){
        return lastScrubStart;
    }

    @Override
        public void addEventGenerator(EventGenerator generator){
            if(generator.getName().equals("latentErrorGenerator"))
                this.latentErrorGenerator = generator;
            else if (generator.getName().equals("scrubGenerator"))
                this.scrubGenerator = generator;
            else 
                super.addEventGenerator(generator);
        }

    @Override
        public void generateEvents(EventQueue resultEvents, double startTime, double endTime, boolean reset){

            if (Double.isInfinite(startTime) || Double.isNaN(startTime)) throw new RuntimeException("starttime = Inf or NAN");
            if (Double.isInfinite(endTime) || Double.isNaN(endTime)) throw new RuntimeException("endtime = Inf or NaN");

            double currentTime = startTime;

            if(children!=null&&children.size()!=0)
                throw new RuntimeException("Disk should not have any children");

            while(true){
                if (lastRecoveryTime<0) throw new RuntimeException("Negative last Recover Time");
                /**THE LOOP BELOW IS WHAT MAKES THE DIFFERENCE FOR AVOIDING  
                 * WEIRD AMPLIFICATION OF # OF FAILURES WHEN HAVING MACHINE FAILURES
                 * THE REASON IS AS FOLLOWS: 
                 * WHEN generateEvents IS CALLED ONCE FOR THE WHOLE DURATION OF THE SIMULATION
                 * (AS WHEN THERE ARE NO MACHINE FAILURES), THIS LOOP WILL NEVER BE EXECUTED.
                 * BUT WHEN MACHINES FAIL, THIS FUNCTION IS CALLED FOR THE TIME INTERVAL 
                 * BETWEEN MACHINE RECOVERY AND SECOND FAILURE. THE FIRST TIME THE DISK 
                 * FAILURE EVENT GENERATED, IT MAY OCCURE AFTER THE MACHINE FAILURE EVENT, SO IT IS DISCARDED
                 * WHEN IT IS CALLED FOR THE NEXT TIME INTERVAL, THE NEW FAILURE EVENT MIGHT BE GENERATED
                 * TO BE BEFORE THE CURRENT START OF THE CURRENT INTERVAL. IT IS TEMPTING TO ROUND THAT EVENT
                 * TO THE START OF THE INTERVAL, BUT THEN IT OCCURS CONCURRENTLY TO MANY DISKS.  
                 * SO THE CRITICAL ADDITION IS THIS LOOP, WHICH EFFECTIVELY FORCES THE PROPER GENERATION
                 * OF THE EVENT, WHICH IS CONSISTENT WITH THE PREVIOUSLY GENERATED ONE THAT WAS DISCARDED. 
                 */
                double failureTime =0;
                do{
                    failureTime=failureGenerator.generateNextEvent(lastRecoveryTime);
                }while(failureTime<startTime);

                if(failureTime>endTime){
                    // no reset here - keep generating events since the last recover
                    generateLatentErrors(resultEvents, currentTime, endTime);
                    generateScrub(resultEvents, currentTime, endTime);
                    break;
                }

                if (failureTime<startTime||failureTime>endTime) throw new RuntimeException("wrong time range");

                Event failEvent = new Event(Event.EventType.Failure, failureTime, this);
                resultEvents.addEvent(failEvent);

                double recoveryTime = generateRecoveryEvent(resultEvents, failureTime, endTime);
                if (recoveryTime<0) throw new RuntimeException("recovery time is negative");
                failEvent.nextRecoveryTime = recoveryTime; // give the failure event knowledge of when recovery happens

                //generate latent errors from the current time to the time of the generated failure 
                generateLatentErrors(resultEvents, currentTime, failureTime);
                // lifetime of a latent error starts when the disk is reconstructed  
                latentErrorGenerator.reset(recoveryTime);

                // scrubs get generated depending on the scrub frequency, starting from the previous
                // scrub finish event. 
                generateScrub(resultEvents, currentTime, failureTime);

                //scrub generator is reset on the next recovery frm the disk error
                scrubGenerator.reset(lastRecoveryTime); 

                // move the clocks: next iteration starts from the next recovery
                currentTime = lastRecoveryTime;
                if (currentTime<0) throw new RuntimeException("crecoveryurrent time is negative");
            }
        }

    public double generateRecoveryEvent(EventQueue resultEvents, double failureTime, double endTime) {
        if (endTime<0||failureTime<0) throw new RuntimeException("end time or failure time <0");
        if (Double.isInfinite(failureTime) || Double.isNaN(failureTime)) throw new RuntimeException("starttime = Inf or NAN");
        if (Double.isInfinite(endTime) || Double.isNaN(endTime)) throw new RuntimeException("endtime = Inf or NaN");

        recoveryGenerator.reset(failureTime); // generate recovery event
        double recoveryTime = recoveryGenerator.generateNextEvent(failureTime);
        // if recovery falls later than the end time (which is the time of the next failure of the higher-level component
        // we just co-locate the recovery with the failure because the data wil remain unavailable in either case.
        if (recoveryTime>endTime) recoveryTime=endTime;
        lastRecoveryTime=recoveryTime;
        if (lastRecoveryTime<0) throw new RuntimeException("recovery time is negative");
        resultEvents.addEvent(new Event(Event.EventType.Recovered, recoveryTime, this));
        return recoveryTime;
    }

    private void generateLatentErrors(EventQueue resultEvents, double startTime, double endTime){
        if (Double.isInfinite(startTime) || Double.isNaN(startTime)) throw new RuntimeException("starttime = Inf or NAN");
        if (Double.isInfinite(endTime) || Double.isNaN(endTime)) throw new RuntimeException("endtime = Inf or NaN");

        double currentTime = startTime;
        while(true){
            double latentErrorTime = latentErrorGenerator.generateNextEvent(currentTime);
            if (Double.isInfinite(latentErrorTime)) break;
            if (Double.isInfinite(currentTime) || Double.isNaN(currentTime)) throw new RuntimeException("current time is infinitiy or -infinity");
            if (Double.isInfinite(latentErrorTime) || Double.isNaN(latentErrorTime)) throw new RuntimeException("current time is infinitiy or -infinity");

            currentTime = latentErrorTime;
            if(currentTime>endTime)
                break;
            resultEvents.addEvent(new Event(Event.EventType.LatentDefect, currentTime, this));
        }
    }

    private void generateScrub(EventQueue resultEvents, double startTime, double endTime){
        //Generate scrubs
        if (Double.isInfinite(startTime) || Double.isNaN(startTime)) throw new RuntimeException("starttime = Inf or NAN");
        if (Double.isInfinite(endTime) || Double.isNaN(endTime)) throw new RuntimeException("endtime = Inf or NaN");

        double currentTime=startTime;
        while(true){

            double scrubTime = scrubGenerator.generateNextEvent(currentTime);
            // if scrubTime is later than now, it will be regenerated next time
            if (scrubTime>endTime) break;

            // scrubTime could be earlier than the current time, if its constant generator
            assert (scrubTime>=startTime) ;

            Event scrubEnd = new Event(Event.EventType.ScrubComplete, scrubTime, this);
            resultEvents.addEvent(scrubEnd);

            Event scrubStart = new Event(Event.EventType.ScrubStart, scrubTime+(1E-5), this);
            resultEvents.addEvent(scrubStart);
            currentTime=scrubTime;
            scrubGenerator.reset(currentTime);
        }
    }
}
