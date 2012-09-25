/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.eventHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.TreeSet;

import simulator.Configuration;
import simulator.Event;
import simulator.EventHandler;
import simulator.Metadata;
import simulator.Result;
import simulator.Unit;
import simulator.unit.*;
import simulator.EventQueue;

public class RandomDistributeEventHandler2 implements EventHandler {

    private boolean myAssert(boolean expression){
        if (!expression) throw new RuntimeException("myAssertion failed");
        return true;
    }
    private long minAvCount=Long.MAX_VALUE;

    private final int LOST_SLICE = -100; 

    private final int n = Configuration.n;
    private final int k = Configuration.k;
    private final int numChunksDiffRacks = Configuration.numChunksDiffRacks;

    // A slice is recovered when recoveryThreshold number of chunks are 'lost',
    //  where 'lost' can include durability events (disk failure, latent failure),
    //  as well as availability events (temporary machine failure) if 
    //  availabilityCountsForRecovery is set to true (see below). However, slice 
    //  recovery can take two forms: 
    //      1. If lazyRecovery is set to false: only the chunk that is in the
    //          current disk being recovered, is recovered.
    //      2. If lazyRecovery is set to true: all chunks of this slice that are
    //          known to be damaged, are recovered.
    private final boolean lazyRecovery = Configuration.lazyRecovery; //LZR
    private int recoveryThreshold = Configuration.recoveryThreshold; //LZR

    // Lazy recovery threshold can be defined in one of two ways:
    //   1. a slice is recovered when some number of *durability* events happen
    //   2. a slice is recovered when some number of durability and/or availability events happen
    //   where durability events include permanent machine failures, or disk failures, while
    //      availabilty events are temporary machine failures
    // This parameter -- availabilityCountsForRecovery -- determines which policy is followed.
    //   If true, then definition #2 is followed, else definition #1 is followed.
    private final boolean availabilityCountsForRecovery = Configuration.availabilityCountsForRecovery;

    private byte[] availableCount = null;
    private byte[] durableCount = null;
    private boolean[] latentDefect = null;
    private boolean[] knownLatentDefect = null;
    private ArrayList<ArrayList<Unit>> sliceLocations; //LZR

    private int unavailableSliceCount = 0;
    private int unDurableSliceCount = 0;

    // There is an anomaly (logical bug?) that is possible in the current implementation:
    //  If a machine A suffers a temporary failure at time t, and between t and t+failTimeout,
    //      if a recovery event happens which affects a slice which is also hosted on machine
    //      A, then that recovery event may rebuild chunks of the slice that were made
    //      unavailable by machine A's failure. This should not happen, as technically, 
    //      machine A's failure should not register as a failure until t+failTimeout.
    //  This count -- anomalousAvailableCount -- keeps track of how many times this happens
    private int anomalousAvailableCount = 0;

    private long currentSliceDegraded=0;
    private long currentAvailabSliceDegraded=0;

    private double recoveryBandwidthCap = Configuration.recoveryBandwidthCap; // cap on recovery bandwidth, in MB/hr
    private double currentRecoveryBandwidth=0; // instantaneous total recovery b/w, in MB/hr, not to exceed above cap
    private double maxRecoveryBandwidth=0; // max instantaneous recovery b/w, in MB/hr

    private int snapshotYear=1; // current counter of years to print histogram

    private double computeReconstructionBandwdith(int numMissingBlocks){
        if (Configuration.bandwidthEfficientScheme){
            if (numMissingBlocks==1) return Configuration.k/2*Configuration.chunkSize;
        }

        return Configuration.chunkSize*(Configuration.k+numMissingBlocks-1);  
    }

    private class Recovery{
        public double start;
        public double  end;
        public double dataRecovered;
        Recovery(double start, double end, double dataRecovered){
            this.start=start; this.end=end; this.dataRecovered=dataRecovered;
        }
        double bandwidht(){
            return ((dataRecovered/(end-start))*24/1024); // scale it to GB/day;
        }
    }

    private TreeMap<Double,LinkedList<Recovery>> bandwidthList=new TreeMap<Double,LinkedList<Recovery>>();

    private void putBandwidthList(double key,Recovery r){
        LinkedList<Recovery> l=null;
        if (bandwidthList.containsKey(key)){
            l=bandwidthList.get(key);
        }else{
            l=new LinkedList<Recovery>();
            bandwidthList.put(key,l);
        }
        l.add(r);
    }

    double maxBw=0;
    void addBandwidthStat(Recovery r){
        if (maxBw<r.bandwidht()) {
            maxBw=r.bandwidht();
            System.err.println("Max bw now is: "+maxBw);
        }

        putBandwidthList(r.start,r);
        putBandwidthList(r.end,r);
    }

    void analyzeBandwidth(){
        double currentBandwidth=0;
        LinkedList<tuple> tmpBwList=new LinkedList<tuple>();
        do {
            Double key=bandwidthList.firstKey(); 
            LinkedList<Recovery> rList=bandwidthList.firstEntry().getValue();
            bandwidthList.remove(key);
            for( Recovery r:rList){
                boolean start=(key==r.start);
                if (start){
                    currentBandwidth+=r.bandwidht();
                }else{
                    currentBandwidth-=r.bandwidht();
                }
            }

            if (currentBandwidth<0 && currentBandwidth>-1) currentBandwidth=0;
            if (currentBandwidth<0) throw new RuntimeException("negative bandwdith count");
            tmpBwList.add(new tuple(key,currentBandwidth));
        }while(!bandwidthList.isEmpty());

        printDegradedStat(tmpBwList,"Avg_bandwidth_", "GBPerday");
    }

    void sliceRecovered(int sliceIndex){
        if (durableCount[sliceIndex]-(latentDefect[sliceIndex]?1:0) ==n) { 
            currentSliceDegraded--; 
        }
        sliceRecoveredAvailability(sliceIndex);
    }

    void sliceDegraded(int sliceIndex){
        if (durableCount[sliceIndex]-(latentDefect[sliceIndex]?1:0) ==n) { 
            currentSliceDegraded++; 
        }
        sliceDegradedAvailability(sliceIndex);
    }

    void sliceRecoveredAvailability(int sliceIndex){
        if (k==1) return; // replication is not affected by this
        int undurable=n-durableCount[sliceIndex]+(latentDefect[sliceIndex]?1:0);
        int unavailable=n-availableCount[sliceIndex];
        if (undurable ==0 && unavailable ==0 ) { 
            currentAvailabSliceDegraded--; 
        }else{
            if (unavailable==0)  totalIncompleteRecoveryAttempts++;
        }
    }

    void sliceDegradedAvailability(int sliceIndex){
        if (k==1) return; // replication is not affected by this
        int undurable=n-durableCount[sliceIndex]+(latentDefect[sliceIndex]?1:0);
        int unavailable=n-availableCount[sliceIndex];
        if (undurable ==0 && unavailable ==0 ) { 
            currentAvailabSliceDegraded++; 
        }

    }

    public class tuple{
        double _time;
        double _count;
        tuple(double time,double count){
            _time=time;_count=count;
        }
    }

    private LinkedList<tuple> slicesDegratedList=new LinkedList<tuple>();
    private LinkedList<tuple> slicesDegradedAvailList=new LinkedList<tuple>();

    public int totalLatentFailures=0;
    public  int totalScrubs=0;
    public  int totalScrubRepairs=0;
    public  int totalDiskFailures=0;
    public  int totalDiskRepairs=0;
    public  int totalMachineFailures=0;
    public  int totalMachineRepairs=0;
    public  int totalPermMachineFailures=0;
    public  int totalShortTempMachineFailures=0;
    public  int totalLongTempMachineFailures=0;
    public  int totalMachineFailuresDueToRackFailures=0;
    public  int totalEagerMachineRepairs=0;
    public  int totalEagerSliceRepairs=0;
    public  int totalSkippedLatent=0;
    public long totalIncompleteRecoveryAttempts=0;

    private int cccccccccccccc=0;

    private void printPerYearStat(double[] perDayStat, String description){
        double d=0;
        long year=365;
        for(int time=1;time<perDayStat.length;time++){
            d+=perDayStat[time];
            if (time%year==0) {
                d/=365;
                System.out.println(description+" "+time/year+" "+d);
                d=0;
            }
        }
        System.out.println(description+" "+perDayStat.length/year+" "+d/365);
    }

    private void printDegradedStat(LinkedList<tuple> degraded,String description, String unit){
        double currentSampleAverage=0;
        long currentTime=0;

        int samplingPeriod=24; // every how much time (hours) we want to average the input and extrapolate in between

        double valuesPerSample[]=new double[samplingPeriod*60];
        int samples=(int)Configuration.totalTime/24;
        if (Configuration.totalTime%24!=0) samples++;
        samples++;

        double daySamples[]=new double[(int)(samples)];
        double previousWindowValue=0;
        double avgOfAvgs=0;
        double avgCount=0;
        double max=0;

        Iterator<tuple> it=degraded.iterator();
        tuple t=it.hasNext()?it.next():null;

        while(t!=null){
            Arrays.fill(valuesPerSample, 0);

            for(int i=0;i<samplingPeriod*60&&t!=null;i++){
                int perSampleCount=0;
                while(true){
                    if (t._time>currentTime+i/60){ // got no new sample
                        perSampleCount=0;
                        valuesPerSample[i]=previousWindowValue;
                        break;
                    }else{
                        valuesPerSample[i]=(valuesPerSample[i]*perSampleCount+t._count)/(perSampleCount+1);
                        previousWindowValue=t._count;
                        perSampleCount++;
                        if (!it.hasNext()) { t=null;break;}
                        t=it.next();
                    }
                }
            }
            // single sampling period is done
            currentSampleAverage=0;
            for(int i=0;i<samplingPeriod*60;i++){
                currentSampleAverage+=valuesPerSample[i];
                if (max<valuesPerSample[i]) max=valuesPerSample[i];
            }
            currentSampleAverage/=(samplingPeriod*60);

            if ((int)(currentTime/24)>= daySamples.length) break;

            daySamples[(int)(currentTime/24)]=currentSampleAverage;
            currentTime+=samplingPeriod;
            avgOfAvgs+=currentSampleAverage;
            avgCount++;
        }
        //compute stdev
        avgOfAvgs/=avgCount;
        double stdev=0;
        for(double val:daySamples){
            stdev+=(val-avgOfAvgs)*(val-avgOfAvgs);
        }

        System.out.println(description+"_per_"+samplingPeriod+"h_" +unit+" " +avgOfAvgs + " stdev:"
                +Math.sqrt(stdev/(daySamples.length-1)) + " max:"+max);
        printPerYearStat(daySamples,description);

    }

    public Result end() {
        Result ret = new Result();
        ret.unAvailableCount = this.unavailableSliceCount;
        ret.unDurableCount = this.unDurableSliceCount;
        System.out.println("anomalousAvailableCount "+anomalousAvailableCount);
        System.out.println("totalLatentFailures "+totalLatentFailures);
        System.out.println("totalScrubs "+totalScrubs);
        System.out.println("totalScrubsRepairs "+ totalScrubRepairs);
        System.out.println("totalDiskFailures "+totalDiskFailures);
        System.out.println("totalDiskRepairs "+totalDiskRepairs);
        System.out.println("totalMachineFailures "+totalMachineFailures);
        System.out.println("totalMachineRepairs "+totalMachineRepairs);
        System.out.println("totalPermMachineFailures "+totalPermMachineFailures);
        System.out.println("totalShortTempMachineFailures "+totalShortTempMachineFailures);
        System.out.println("totalLongTempMachineFailures "+totalLongTempMachineFailures);
        System.out.println("totalMachineFailuresDueToRackFailures "+totalMachineFailuresDueToRackFailures);
        System.out.println("totalEagerMachineRepairs "+totalEagerMachineRepairs);
        System.out.println("totalEagerSliceRepairs "+totalEagerSliceRepairs);
        System.out.println("totalSkippedLatent "+ totalSkippedLatent);
        System.out.println("totalIncompleteRecovery "+ totalIncompleteRecoveryAttempts);
        System.out.println("");
        System.out.println("maxRecoveryBandwidth "+ maxRecoveryBandwidth);
        System.out.println("ccccc "+cccccccccccccc);

        System.out.println("durability " + (1-((double)unDurableSliceCount/(double)availableCount.length))*100.0 + "%" );

        printDegradedStat(slicesDegratedList,"Avg_durable_degraded_","slices");
        printDegradedStat(slicesDegradedAvailList, "Avg_available_degraded_","slices");
        analyzeBandwidth();

        return ret;
    }

    public void handleEvent(Event e, EventQueue queue) {
        if (e.getType() == Event.EventType.Failure) {
            handleFailure(e.getUnit(), e.getTime(), e, queue);
        } else if (e.getType() == Event.EventType.Recovered) {
            handleRecovery(e.getUnit(), e.getTime(), e);
        } else if (e.getType() == Event.EventType.EagerRecoveryStart) {
            handleEagerRecoveryStart(e.getUnit(), e.getTime(), e, queue);
        } else if (e.getType() == Event.EventType.EagerRecoveryInstallment) {
            handleEagerRecoveryInstallment(e.getUnit(), e.getTime(), e);
        } else if (e.getType() == Event.EventType.LatentDefect) {
            handleLatentDefect(e.getUnit(), e.getTime(), e);
        } else if (e.getType() == Event.EventType.ScrubStart) {
            handleScrubStart(e.getUnit(), e.getTime(), e);
        } else if (e.getType() == Event.EventType.ScrubComplete) {
            handleScrubComplete(e.getUnit(), e.getTime(), e);
        } else {
            throw new RuntimeException("Unknown event " + e.getType());
        }
    }

    private long computeHistogramBool(boolean[] data, String what){
        long[] histogram=new long[2];
        Arrays.fill(histogram,0);
        for(int i=0;i<data.length;i++){
            histogram[data[i]==true?1:0]++;
        }
        System.out.println(what);
        for(int i=0;i<histogram.length;i++){
            System.out.print(i+ "->" + histogram[i]+" ");
        }
        return histogram[1];
    }

    private int computeHistogram(byte[] data, int maxVal,String what){
        long[] histogram=new long[maxVal+1];
        Arrays.fill(histogram,0);
        for(int i=1;i<data.length;i++){
            histogram[data[i]]++;
        }
        System.out.println(what);
        int lessThanMax=0;
        for(int i=1;i<histogram.length;i++){
            System.out.print(i+ "->" + histogram[i]+" ");
            if (i<maxVal) lessThanMax+=histogram[i];
        }
        return lessThanMax;
    }

    private void handleFailure(Unit u, double time, Event e, EventQueue queue) {
        if(e.ignore)
            return;

        if (u instanceof Machine) {
            totalMachineFailures++;
            u.setLastFailureTime(e.getTime());

            if(e.info == 3) { 
                totalPermMachineFailures++;
            } else { // this is not a permanent failure
                // temporary machine failures affect slice availability
                // whereas permanent machine failures affect slice durability
                // the former effect is simulated here, while the latter effect
                // is simulated by the disk failures caused by permanent machine 
                // failure
                if(e.info == 1)
                    totalShortTempMachineFailures++;
                else if(e.info == 2)
                    totalLongTempMachineFailures++;
                else { // machine failure due to rack failure
                    totalMachineFailuresDueToRackFailures++;
                    if(e.nextRecoveryTime - e.getTime() <= ((Machine)u).failTimeout)
                        totalShortTempMachineFailures++;
                    else { // this is a long failure
                        totalLongTempMachineFailures++;
                        if(((Machine)u).eagerRecoveryEnabled) {
                            double eagerRecoveryStartTime = e.getTime()+((Machine)u).failTimeout;
                            Event eagerRecoveryStartEvent = new Event(Event.EventType.EagerRecoveryStart, eagerRecoveryStartTime, (Machine)u);
                            eagerRecoveryStartEvent.nextRecoveryTime = e.nextRecoveryTime-(1E-5);
                            queue.addEvent(eagerRecoveryStartEvent);
                        }
                    }
                }

                for (Unit child : u.getChildren()) {
                    if (child.getMetadata().sliceCount == 0){
                        System.err.println("Lost machine failure");
                        continue;
                    }

                    for (int i=0; i<child.getMetadata().sliceCount; i++) {
                        int sliceIndex = child.getMetadata().slices[i];

                        if (durableCount[sliceIndex]==LOST_SLICE) continue;

                        sliceDegradedAvailability(sliceIndex); // must be called before counters are updated
                        availableCount[sliceIndex]--;

                        myAssert (availableCount[sliceIndex] >= 0);
                        if (availableCount[sliceIndex] < k) {
                            unavailableSliceCount++;
                        }
                    }
                }
                slicesDegradedAvailList.add(new tuple(e.getTime(),currentAvailabSliceDegraded));
            }

        } else if (u instanceof Disk) {
            totalDiskFailures++;
            u.setLastFailureTime(e.getTime());
            double projectedBandwidthNeed = 0; // needed to compute projected recovery b/w needed
            for (int i=0; i<u.getMetadata().sliceCount; i++) {

                int sliceIndex = u.getMetadata().slices[i];
                if (durableCount[sliceIndex]==LOST_SLICE) continue; 
                if (u.getMetadata().nonexistentSlices!=null && u.getMetadata().nonexistentSlices.contains(sliceIndex)) continue;


                sliceDegraded(sliceIndex); // MUST precent any change to the counts

                durableCount[sliceIndex]--;
                if(u.getMetadata().nonexistentSlices==null)
                    u.getMetadata().nonexistentSlices = new HashSet<Integer>();
                u.getMetadata().nonexistentSlices.add(sliceIndex);

                myAssert(durableCount[sliceIndex]>=0);

                // All latent failure son this disk should be cancelled because they disappear with the disk replacement. 
                if (u.getMetadata().defectiveSlices!=null&&u.getMetadata().defectiveSlices.contains(sliceIndex)) 
                    latentDefect[sliceIndex] = false;
                if (u.getMetadata().knownDefectiveSlices!=null&&u.getMetadata().knownDefectiveSlices.contains(sliceIndex)) 
                    knownLatentDefect[sliceIndex] = false;

                if (durableCount[sliceIndex] < k) {
                    System.out.println(time +":slice "+sliceIndex+ " durCount: "+ durableCount[sliceIndex] + " latDefect " + 
                            latentDefect[sliceIndex] +"  due to disk " + u.getID());
                    durableCount[sliceIndex] = LOST_SLICE;
                    unDurableSliceCount++;
                    continue;
                }
                if (durableCount[sliceIndex] == k && latentDefect[sliceIndex]) {
                    System.out.println(time +":slice "+sliceIndex+ " durCount: "+ durableCount[sliceIndex] + " latDefect " + 
                            latentDefect[sliceIndex] +"  due to latent error and disk " + u.getID());
                    durableCount[sliceIndex] = LOST_SLICE;
                    unDurableSliceCount++;
                }

                // is this slice one that needs recovering? if so, how much data to recover?
                if(durableCount[sliceIndex] != LOST_SLICE) {
                    boolean thresholdCrossed = false;
                    int numUndurable = n-durableCount[sliceIndex];
                    if(knownLatentDefect[sliceIndex]) numUndurable++;
                    if(numUndurable >= n-recoveryThreshold) thresholdCrossed = true;
                    int numUnavailable = 0;
                    if(availabilityCountsForRecovery) {
                        numUnavailable = n-availableCount[sliceIndex];
                        if(numUndurable+numUnavailable >= n-recoveryThreshold) thresholdCrossed = true;
                    }
                    if(thresholdCrossed) { 
                        projectedBandwidthNeed += computeReconstructionBandwdith(numUndurable+numUnavailable);
                    }

                }

            }
            // current recovery bandwidth goes up by projectedBandwidthNeed
            projectedBandwidthNeed = projectedBandwidthNeed/(e.nextRecoveryTime-e.getTime());
            u.setLastBandwidthNeed(projectedBandwidthNeed);
            myAssert(currentRecoveryBandwidth >= 0);
            currentRecoveryBandwidth += projectedBandwidthNeed;
            myAssert(currentRecoveryBandwidth>=0);
            if(currentRecoveryBandwidth > maxRecoveryBandwidth) maxRecoveryBandwidth = currentRecoveryBandwidth;
            myAssert(currentRecoveryBandwidth>=0);
            u.getMetadata().defectiveSlices=null;
            u.getMetadata().knownDefectiveSlices=null;

            slicesDegratedList.add(new tuple(e.getTime(),currentSliceDegraded));
            slicesDegradedAvailList.add(new tuple(e.getTime(),currentAvailabSliceDegraded));
        } else {
            for (Unit tmp : u.getChildren())
                handleFailure(tmp, time, e, queue);
        }
    }

    private void handleRecovery(Unit u, double time, Event e) {
        if(e.ignore)
            return;

        if (u instanceof Machine) {
            totalMachineRepairs++;

            if(e.info != 3) { // this is a recovery from a temporary machine failure
                // recovery from temporary machine failure increments slice availability,
                // whereas recovery from permanent machine failure improves slice durability.
                // the former effect is simulated here, while the latter effect is simulated
                // in disk recoveries generated by this permanent machine recovery
                for (Unit child : u.getChildren()) {
                    for (int i=0; i<child.getMetadata().sliceCount; i++) {
                        int sliceIndex = child.getMetadata().slices[i];
                        if (durableCount[sliceIndex] == LOST_SLICE) continue;

                        // We are going to check, using the 'info' field of the event,
                        //  whether this was a temporary machine failure of short duration.
                        //  If so, then all of the availabilityCounts should be less than n
                        //  If they are not, then anomalousAvailableCount will be incremented
                        if(availableCount[sliceIndex] < n){
                            availableCount[sliceIndex]++;

                            sliceRecoveredAvailability(sliceIndex);
                        }
                        else if(e.info == 1) {// availableCount is >= n even tho it's a temp&short failure 
                            anomalousAvailableCount++;
                            //System.out.println("AnomalousAvailableCount: " + anomalousAvailableCount + ": Machine recovery: " + e.toString());
                        }
                    }
                }
                slicesDegradedAvailList.add(new tuple(e.getTime(),currentAvailabSliceDegraded));
            }
        } else if (u instanceof Disk) {

            totalDiskRepairs++;
            // this disk finished recovering, so decrement current recov b/w
            currentRecoveryBandwidth -= u.getLastBandwidthNeed();
            if (currentRecoveryBandwidth>-1&& currentRecoveryBandwidth<0) currentRecoveryBandwidth=0;
            myAssert(currentRecoveryBandwidth >= 0);

            double transferRequired=0;
            for (int i=0; i<u.getMetadata().sliceCount; i++) {
                int sliceIndex = u.getMetadata().slices[i];
                if (durableCount[sliceIndex] == LOST_SLICE) continue;


                boolean thresholdCrossed = false;
                int actualThreshold=recoveryThreshold;
                if (Configuration.lazyOnlyAvailable) actualThreshold=n-1;
                if (currentSliceDegraded < Configuration.maxDegradedSlices*Configuration.totalSlices ){
                    actualThreshold=recoveryThreshold;
                }
                int numUndurable = n - durableCount[sliceIndex];
                if(knownLatentDefect[sliceIndex]) numUndurable++;
                if(numUndurable >= n - actualThreshold) thresholdCrossed = true;
                // If availabilityCountsForRecovery is true, then we compute laziness threshold differently 
                if(availabilityCountsForRecovery) {
                    int numUnavailable = n - availableCount[sliceIndex]; 
                    if(numUndurable + numUnavailable >= n - actualThreshold) thresholdCrossed = true;
                } 

                if(thresholdCrossed) {
                    if(lazyRecovery) {
                        // recover all replicas of this slice
                        int chunksRecovered=handleSliceRecovery(sliceIndex,e,true); // this is a durability failure
                        if (chunksRecovered>0) {
                            // transfer required for 1 chunk is k, for 2 is k+1 , etc..
                            transferRequired+=computeReconstructionBandwdith(chunksRecovered);
                        }

                    } else {
                        // recover only this replica
                        if(u.getMetadata().nonexistentSlices!=null && u.getMetadata().nonexistentSlices.contains(sliceIndex)) {
                            u.getMetadata().nonexistentSlices.remove(sliceIndex);
                            durableCount[sliceIndex]++;
                            transferRequired+=computeReconstructionBandwdith(1);
                        }
                    }
                    // must come after all counters are updated
                    sliceRecovered(sliceIndex);
                }

            }
            slicesDegratedList.add(new tuple(e.getTime(),currentSliceDegraded));
            slicesDegradedAvailList.add(new tuple(e.getTime(),currentAvailabSliceDegraded));

            addBandwidthStat(new Recovery(u.getLastFailureTime(),e.getTime(),transferRequired));
        } else {
            for (Unit tmp : u.getChildren())
                handleRecovery(tmp, time, e);
        }
    }

    private void handleEagerRecoveryStart(Unit u, double time, Event e, EventQueue queue) {
        myAssert (u instanceof Machine);
        totalEagerMachineRepairs++;
        u.setLastFailureTime(e.getTime());
        double originalFailureTime=e.getTime(); /// it's not the actual failure, but rather recovery, but itsclose

        // Eager recovery begins now, and ends at time e.nextRecoveryTime (which is when the machine recovers).
        // Recovery rate will be (recoveryBandwidthCap - currentRecoveryBandwidth) MB/hr.
        // Therefore, total number of chunks that can be recovered = eager recovery duration * recovery rate.
        // This happens in installments, of installmentSize number of chunks each.
        // The last installment will have (total num chunks % installmentSize) number of chunks

        myAssert(e.nextRecoveryTime - e.getTime() > 0);
        myAssert(currentRecoveryBandwidth >= 0);
        double recoveryRate = recoveryBandwidthCap - currentRecoveryBandwidth;
        if(recoveryRate <= 0) 
            return;

        int numChunksToRecover = (int) (recoveryRate/Configuration.chunkSize * (e.nextRecoveryTime - e.getTime()));
        if(numChunksToRecover < 1) 
            return;

        recoveryRate = ((double)numChunksToRecover)*Configuration.chunkSize/(e.nextRecoveryTime-e.getTime());
        myAssert(recoveryRate>=0);
        currentRecoveryBandwidth += recoveryRate;
        myAssert(currentRecoveryBandwidth>=0);
        if(currentRecoveryBandwidth > maxRecoveryBandwidth) maxRecoveryBandwidth = currentRecoveryBandwidth;

        int currInstallmentSize = Configuration.installmentSize;
        if(numChunksToRecover < Configuration.installmentSize) 
            currInstallmentSize = numChunksToRecover;


        SliceSet sliceInstallment = null;
        try {
            sliceInstallment = (SliceSet)Class.forName("simulator.unit.SliceSet").newInstance();
            sliceInstallment.init("SliceSet-"+u.toString(), new ArrayList<Integer>());
            sliceInstallment.setLastFailureTime(u.getLastFailureTime());
            sliceInstallment.setOriginalFailureTime(originalFailureTime);
        } catch (Exception excep) {
            System.err.println("Error in eager recovery: " + excep.getMessage());
            System.exit(-1);
        }

        int totalNumChunksAddedForRepair = 0;
        int numChunksAddedToCurrInstallment = 0;
        double currTime = time;
        for (Unit child: u.getChildren()) {
            for(int i=0; i<child.getMetadata().sliceCount; i++) {
                int sliceIndex = child.getMetadata().slices[i];
                // When this machine failed, it decremented the availability count
                //  of all its slices. This eager recovery is the first point in
                //  time that this machine failure has been 'recognized' by the system
                //  (since this is when the timeout expires). So if at this point
                //  we find any of the availability counts NOT less than n, then
                //  we need to count it as an anomaly
                if(availableCount[sliceIndex] >= n) {
                    anomalousAvailableCount++;
                }
                if(durableCount[sliceIndex] == LOST_SLICE)
                    continue;

                boolean thresholdCrossed = false;
                int numUndurable = n-durableCount[sliceIndex];
                if(knownLatentDefect[sliceIndex]) numUndurable++;
                int actualThreshold=recoveryThreshold;
                double expectedRecoveryTime=currTime+currInstallmentSize*Configuration.chunkSize/recoveryRate;
                actualThreshold=Configuration.getAvailableLazyThreshold(expectedRecoveryTime-sliceInstallment.getOriginalFailureTime());

                if(numUndurable >= n-actualThreshold) 
                    thresholdCrossed = true;

                int numUnavailable = 0;
                if(availabilityCountsForRecovery) {
                    numUnavailable = n-availableCount[sliceIndex]; 
                    if(numUndurable+numUnavailable >= n-actualThreshold) // threshold reached to trigger eager recovery of this slice
                        thresholdCrossed = true;
                }
                if(thresholdCrossed) {
                    sliceInstallment.slices.add(sliceIndex);
                    totalNumChunksAddedForRepair += k + numUndurable + numUnavailable - 1;
                    numChunksAddedToCurrInstallment += k + numUndurable + numUnavailable - 1;
                    if(numChunksAddedToCurrInstallment >= currInstallmentSize-k) {
                        currTime += numChunksAddedToCurrInstallment*Configuration.chunkSize/recoveryRate;
                        queue.addEvent(new Event(Event.EventType.EagerRecoveryInstallment, currTime, sliceInstallment, false));
                        if(totalNumChunksAddedForRepair >= numChunksToRecover-k) {
                            // the last installment must update recovery bandwidth
                            sliceInstallment.setLastBandwidthNeed(recoveryRate);
                            return;
                        }
                        currInstallmentSize = Configuration.installmentSize;
                        if(numChunksToRecover-totalNumChunksAddedForRepair < Configuration.installmentSize) 
                            currInstallmentSize = numChunksToRecover - totalNumChunksAddedForRepair;
                        try {
                            sliceInstallment = (SliceSet)Class.forName("simulator.unit.SliceSet").newInstance();
                            sliceInstallment.init("SliceSet-"+u.toString(), new ArrayList<Integer>());
                            sliceInstallment.setLastFailureTime(currTime);
                            sliceInstallment.setOriginalFailureTime(originalFailureTime);
                            sliceInstallment.setLastBandwidthNeed(-1);
                        } catch (Exception excep) {
                            System.err.println("Error in eager recovery: " + excep.getMessage());
                            System.exit(-1);
                        }
                        numChunksAddedToCurrInstallment = 0;
                    }
                }
            }
        }
        // Arriving at this point in the code means number of slices added < numChunksToRecover
        if(sliceInstallment.slices.size() != 0) {
            currTime += numChunksAddedToCurrInstallment*Configuration.chunkSize/recoveryRate;
            sliceInstallment.setLastBandwidthNeed(recoveryRate);
            queue.addEvent(new Event(Event.EventType.EagerRecoveryInstallment, currTime, sliceInstallment, false));
            return;
        }
        // No slices were found for eager recovery, so undo the current bandwidth need
        currentRecoveryBandwidth -= recoveryRate;
        myAssert(currentRecoveryBandwidth >= 0);
    }

    private void handleEagerRecoveryInstallment(Unit u, double time, Event e) {
        myAssert (u instanceof SliceSet);
        double transferRequired=0;
        if(u.getLastBandwidthNeed() != -1) {// this is the last installment
            currentRecoveryBandwidth -= u.getLastBandwidthNeed();
            if (currentRecoveryBandwidth<0&&currentRecoveryBandwidth>-1) currentRecoveryBandwidth=0;
            myAssert(currentRecoveryBandwidth >= 0);
        }

        for(Integer slice: ((SliceSet)u).slices) {
            int sliceIndex = slice.intValue();
            if(durableCount[sliceIndex] == LOST_SLICE) 
                continue;

            boolean thresholdCrossed = false;
            int numUndurable = n-durableCount[sliceIndex];
            if(knownLatentDefect[sliceIndex]) numUndurable++;
            int actualThreshold=recoveryThreshold;
            SliceSet s=(SliceSet)u;
            actualThreshold=Configuration.getAvailableLazyThreshold(e.getTime()-s.getOriginalFailureTime());


            if(numUndurable >= n-actualThreshold) thresholdCrossed = true;
            if(availabilityCountsForRecovery) {
                int numUnavailable = n-availableCount[sliceIndex];
                if(numUndurable+numUnavailable >= n-actualThreshold) thresholdCrossed = true;
            }
            if(thresholdCrossed) {
                totalEagerSliceRepairs++;
                if(lazyRecovery){
                    int  chunksRecovered=handleSliceRecovery(sliceIndex, e,false);
                    myAssert(availableCount[sliceIndex] == n && durableCount[sliceIndex] ==n );
                    if (numUndurable!=0){
                        sliceRecovered(sliceIndex);

                    }else{
                        sliceRecoveredAvailability(sliceIndex);
                    }
                    transferRequired+=computeReconstructionBandwdith(chunksRecovered);
                } else {
                    if(availableCount[sliceIndex]<n){ 
                        availableCount[sliceIndex]++;
                        transferRequired+=computeReconstructionBandwdith(1);
                        if (numUndurable!=0){
                            sliceRecovered(sliceIndex);
                        }else{
                            sliceRecoveredAvailability(sliceIndex);
                        }

                    }
                }
            } 
        }
        slicesDegratedList.add(new tuple(e.getTime(),currentSliceDegraded));
        slicesDegradedAvailList.add(new tuple(e.getTime(),currentAvailabSliceDegraded));
        addBandwidthStat(new Recovery(u.getLastFailureTime(),e.getTime(),transferRequired));

        u.setLastFailureTime(e.getTime());
    }

    private void handleLatentDefect(Unit u, double time, Event e) {
        if (u instanceof Disk) {
            if (u.getMetadata().sliceCount == 0)
                return;
            myAssert(u.getMetadata().sliceCount>10);

            int index = r.nextInt(Configuration.chunksPerDisk);
            if (index >= u.getMetadata().sliceCount) {
                totalSkippedLatent++;
                return;
            }
            int sliceIndex = u.getMetadata().slices[index];

            if (durableCount[sliceIndex] == LOST_SLICE) {
                totalSkippedLatent++;
                return;
            }
            if (u.getMetadata().nonexistentSlices!=null && u.getMetadata().nonexistentSlices.contains(sliceIndex)) {
                totalSkippedLatent++;
                return;
            }
            if (latentDefect[sliceIndex]) { // a latent defect cannot hit replicas of the same slice multiple times (whp)
                totalSkippedLatent++;
                return;
            }

            myAssert(durableCount[sliceIndex]>=0);
            sliceDegraded(sliceIndex);

            latentDefect[sliceIndex] = true;
            totalLatentFailures++;

            if (u.getMetadata().defectiveSlices == null)
                u.getMetadata().defectiveSlices = new HashSet<Integer>();
            u.getMetadata().defectiveSlices.add(sliceIndex);

            if (durableCount[sliceIndex]==k && latentDefect[sliceIndex]) {
                System.out.println(time +":slice "+sliceIndex+ " durCount: "+ durableCount[sliceIndex] + " latDefect " + 
                        latentDefect[sliceIndex] +"  due to ===latent=== error " + " on disk " + u.getID());
                unDurableSliceCount++;
                durableCount[sliceIndex]=LOST_SLICE;
                u.getMetadata().defectiveSlices.remove(sliceIndex);
            }
        } else
            throw new RuntimeException(
                    "Latent defect should only happen for disk");

        slicesDegratedList.add(new tuple(e.getTime(),currentSliceDegraded));
        slicesDegradedAvailList.add(new tuple(e.getTime(),currentAvailabSliceDegraded));

    }

    private void handleScrubStart(Unit u, double time, Event e) {
        if (u instanceof Disk) {
            ((DiskWithScrubbing)u).setLastScrubStart(time);
            totalScrubs++;
            if (u.getMetadata().defectiveSlices == null)
                return;
            u.getMetadata().knownDefectiveSlices = (HashSet<Integer>)u.getMetadata().defectiveSlices.clone(); 
            for (Integer sliceIndex : u.getMetadata().knownDefectiveSlices)
                knownLatentDefect[sliceIndex] = true;
        } else
            throw new RuntimeException(
                    "Scrub start should only happen for disk");
    }

    private void handleScrubComplete(Unit u, double time, Event e) {
        double transferRequired=0;
        if (u instanceof Disk) {
            if (u.getMetadata().knownDefectiveSlices == null)
                return;
            for (Integer sliceIndex : u.getMetadata().knownDefectiveSlices) {
                totalScrubRepairs++;
                latentDefect[sliceIndex] = false;
                knownLatentDefect[sliceIndex] = false;
                transferRequired+=computeReconstructionBandwdith(1);
                sliceRecovered(sliceIndex);
            }
            u.getMetadata().defectiveSlices = null;
            u.getMetadata().knownDefectiveSlices = null;

        } else
            throw new RuntimeException(
                    "Scrub complete should only happen for disk");

        slicesDegratedList.add(new tuple(e.getTime(),currentSliceDegraded));
        slicesDegradedAvailList.add(new tuple(e.getTime(),currentAvailabSliceDegraded));
        addBandwidthStat(new Recovery(((DiskWithScrubbing)u).getLastScrubStart(),e.getTime(),transferRequired));
    }

    private int handleSliceRecovery(int sliceIndex, Event e, boolean isDurableFailure) { //LZR
        if(durableCount[sliceIndex] == LOST_SLICE) return 0;
        int recovered=0;
        Unit disk;
        for (int i=0; i<n; i++) {
            disk = sliceLocations.get(sliceIndex).get(i);
            if(disk.getMetadata().knownDefectiveSlices!=null && disk.getMetadata().knownDefectiveSlices.contains(sliceIndex)) {
                latentDefect[sliceIndex] = false;
                knownLatentDefect[sliceIndex] = false;
                disk.getMetadata().defectiveSlices.remove(sliceIndex);
                disk.getMetadata().knownDefectiveSlices.remove(sliceIndex);
                recovered++;
            }
            if(disk.getMetadata().nonexistentSlices!=null && disk.getMetadata().nonexistentSlices.contains(sliceIndex)) {
                durableCount[sliceIndex]++;
                disk.getMetadata().nonexistentSlices.remove(sliceIndex);
                recovered++;
            }
        }
        myAssert (knownLatentDefect[sliceIndex] == false && durableCount[sliceIndex] == n);

        if(availabilityCountsForRecovery && (!isDurableFailure || !Configuration.lazyOnlyAvailable)){ // We must also recover from availability failures
            recovered+=n-availableCount[sliceIndex];
            availableCount[sliceIndex] = (byte)n;
        }
        return recovered;
    }

    public void start(Unit root, int totalSlices, int diskCount) {
        distributeSlices(root,totalSlices,diskCount);
    }

    private void distributeSlices(Unit root,int totalSlices,int diskCount) {
        sliceLocations = new ArrayList<ArrayList<Unit>>(); //LZR

        availableCount = new byte[totalSlices];
        durableCount = new byte[totalSlices];
        latentDefect= new boolean[totalSlices];
        knownLatentDefect= new boolean[totalSlices];
        ArrayList<ArrayList<Unit>> disks = new ArrayList<ArrayList<Unit>> (Configuration.rackCount);

        getAllDisks(root, disks);
        ArrayList<ArrayList<Unit>> tmp_racks = new ArrayList<ArrayList<Unit>>(); // keep all used racks

        for (int i = 0; i < totalSlices; i++) {
            sliceLocations.add(new ArrayList<Unit>(n));
            tmp_racks.clear();
            tmp_racks.addAll(disks);
            for (int j = 0; j < n; j++) {
                if(j<numChunksDiffRacks)
                    distributeOneSliceToOneDisk(i,disks, tmp_racks,true);
                else
                    distributeOneSliceToOneDisk(i,disks, tmp_racks,false);
            }
            myAssert (tmp_racks.size() == Configuration.rackCount-Configuration.n);
            myAssert (sliceLocations.get(i).size() == n);
            availableCount[i] = (byte)n;
            durableCount[i] = (byte) n;
        }
        myAssert(sliceLocations.size() == totalSlices);
    }

    private Random r = new Random();

    private void distributeOneSliceToOneDisk(int sliceIndex,
            ArrayList<ArrayList<Unit>> disks, ArrayList<ArrayList<Unit>> availableRacks, boolean separateRacks) {
        int retryCount = 0;
        int sameRackCount = 0;
        int sameDiskCount = 0;
        int fullDiskCount = 0;
        while (true) {
            retryCount++;
            /* choose disk from the right rack */
            if (availableRacks.size()==0) throw new RuntimeException("No racks left");
            int prevRacksIndex=r.nextInt(availableRacks.size());
            ArrayList<Unit> rackDisks=availableRacks.get(prevRacksIndex);

            int diskIndexInRack=r.nextInt(rackDisks.size());
            Unit disk=rackDisks.get(diskIndexInRack);
            if (disk.getMetadata().sliceCount >= Configuration.chunksPerDisk) {
                fullDiskCount++;
                rackDisks.remove(disk);

                if(rackDisks.size()==0){
                    System.err.println("One rack is completely full " + disk.getParent().getParent().getID());
                    availableRacks.remove(rackDisks);
                    disks.remove(rackDisks);
                }	
                if (retryCount > 100) {
                    System.out.println("Unable to distribute slice " + sliceIndex + "; picked full disk " + fullDiskCount + " times, same rack " + sameRackCount + " times, and same disk " + sameDiskCount + " times");
                    throw new RuntimeException("Disk distribution failed");
                }

                continue;
            }
            availableRacks.remove(rackDisks);

            Metadata m = disk.getMetadata();
            if (m.slices == null){
                m.slices = new int[Configuration.chunksPerDisk];
                for(int v=0;v<m.slices.length;v++) {
                    m.slices[v]=-10;
                }
            }
            sliceLocations.get(sliceIndex).add(disk); //LZR
            m.slices[m.sliceCount] = sliceIndex;
            m.sliceCount++;
            break;
        }
    }

    private void getAllDisks(Unit u, ArrayList<ArrayList<Unit>> disks) {

        for (Unit tmp : u.getChildren()) {
            if (tmp instanceof Rack){
                ArrayList<Unit> rackDisks=new ArrayList<Unit>(Configuration.disksPerMachine*Configuration.machinesPerRack);
                getAllDisksInRack((Rack)tmp, rackDisks);
                disks.add(rackDisks);
            }
            else
                getAllDisks(tmp, disks);
        }
    }

    private void getAllDisksInRack(Rack u, ArrayList<Unit> disks) {
        for (Unit tmp : u.getChildren()) {
            for (Unit m: tmp.getChildren()){
                disks.add(m);
            }
        }
    }
}
