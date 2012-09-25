/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator;

import java.util.Random;

public class Configuration {

    public static int rackCount = 0; // computed based on the required total storage
    public static long totalTime = 10*24*365L;
    public static int chunksPerDisk = 3000;

    public static int disksPerMachine = 0;
    public static  int machinesPerRack = 0;

    public static double totalActiveStorage = 0; // in PB
    public static  int n = 0;
    public static  int k = 0;
    public static int totalSlices=0; // global to communicate #slices to all parts of the simulator
    public static  int recoveryThreshold = 0; //if fewer than this number of replicas left, recovery initiated
    public static  boolean availabilityCountsForRecovery = true; //whether or not availability events are recovered from during regular recovery
    public static String eventFile=null; // if this is set to non-null value, events generated will be printed to file (eventFile-<timestamp>.txt), and will not be handled.
    public static int chunkSize = 256; // chunk size in MB

    public static boolean lazyOnlyAvailable=true; // handle durability failures in a NON lazy manner

    public static final int numChunksDiffRacks = 15 ; //it is ensured that, for this number of chunks per slice, no two chunks are on same rack
    public static boolean lazyRecovery = true; // this determines whether, during a slice recovery, all its damaged chunks are recovered, or just the one on the current disk being recovered.

    public static double maxDegradedSlices = 0.1;
    public static double[] availabilityToDurabilityThresholds={0,1, 100000};
    public static double[] recoveryProbability=               {0,0}; 
    private static Random rand=new Random();

    public static int getAvailableLazyThreshold(double timeSinceFailed){
        int thresholdGap=Configuration.n-1-Configuration.recoveryThreshold;
        int i=0;
        for(i=0;i<availabilityToDurabilityThresholds.length-1;i++){
            if (availabilityToDurabilityThresholds[i]<timeSinceFailed &&
                    availabilityToDurabilityThresholds[i+1]>=timeSinceFailed){
                if (i>0){
                    i=i;
                }
                break;
            }
        }
        int thresholdIncrement=thresholdGap*(rand.nextFloat()<recoveryProbability[i]?1:0);
        return Configuration.recoveryThreshold+thresholdIncrement;
    }

    public static final boolean bandwidthEfficientScheme=false;

    public static double recoveryBandwidthCap = 600000000/24; // in MB/hr
    public static int installmentSize = 1000; // max #chunks read/written in each eager recovery installment

    static void print(){
        System.out.println("Configuration: \n totalTime: "+ totalTime 
                + "\n chunksPerDisk: " + chunksPerDisk 
                + "\n chunksPerMachine: " + (chunksPerDisk*disksPerMachine) 
                + "\n n: " + n+" k: "+k 
                + "\n lazyRecovery: " + lazyRecovery 
                + "\n recoveryThreshold: " + recoveryThreshold
                + "\n recoveryBandwidthCap: " + recoveryBandwidthCap
                + "\n installmentSize: "+ installmentSize
                + "\n lazyOnlyAvailable: " + lazyOnlyAvailable
                + "\n availabilityCountsForRecovery: "+availabilityCountsForRecovery
                + "\n numChunksDiffracks: " + numChunksDiffRacks
                + "\n chunkSize: " + chunkSize + "MB"
                + "\n totalActiveStorage: " + totalActiveStorage + "PB"
                + "\n rackCount: " + rackCount
                + "\n disksPerMachine: " + disksPerMachine 
                + "\n bandwidthEfficientReconstruction: " + bandwidthEfficientScheme
                + "\n machinesPerRack: " + machinesPerRack);
    }
}
