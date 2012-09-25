/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator;

import simulator.failure.WeibullGenerator;
import simulator.parser.XmlParser;
import simulator.unit.Disk;
import simulator.unit.Machine;
import simulator.unit.Rack;
import simulator.eventHandler.*;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class Test {

    private static double getMachineFailureGeneratorRate(Unit root){
        Machine m=(Machine)root.getChildren().get(0).getChildren().get(0).getChildren().get(0);
        if (m.getFailureGenerator() instanceof WeibullGenerator){
            return ((WeibullGenerator)m.getFailureGenerator()).getRate();
        }
        return -1;
    }

    public static void main(String[] args) throws Exception {
        // Process command line arguments
        if (args.length!=7) 
            throw new Exception("Usage: #iterations n k lazyThreshold totalActiveStorage(PB) disksPerMachine machinesPerRack");
        int count = Integer.parseInt(args[0]);
        if (count!=1) {
            throw new RuntimeException("Count >1 is buggy");
        }

        Configuration.n = Integer.parseInt(args[1]);
        Configuration.k = Integer.parseInt(args[2]);
        Configuration.recoveryThreshold = Integer.parseInt(args[3]);
        if (Configuration.recoveryThreshold  == Configuration.n ){
            System.err.println("Recovery threshold = # chunks per slice - making it one less: "+ --Configuration.recoveryThreshold);
        }
        if (Configuration.recoveryThreshold  < Configuration.n-1  && !Configuration.lazyRecovery){
            System.err.println("Recovery threshold is less than  # chunks per slice but lazy recovery is disabled - enabling");
            Configuration.lazyRecovery=true;
        }

        Configuration.totalActiveStorage = Double.parseDouble(args[4]);

        Configuration.disksPerMachine=Integer.parseInt(args[5]);
        Configuration.machinesPerRack=Integer.parseInt(args[6]);

        if(Configuration.disksPerMachine<1 || Configuration.disksPerMachine>40) {
            throw new RuntimeException("Cannot have more than 40 disks per machine");
        }
        if (Configuration.machinesPerRack<10 || Configuration.machinesPerRack >80){
            throw new RuntimeException("Cannot have <10 or >80 machines per rack");
        }

        long eventsHandled=0;
        // Compute number of chunks each rack can store
        int chunksPerMachine=Configuration.disksPerMachine*Configuration.chunksPerDisk;
        int chunksPerRack=Configuration.machinesPerRack*chunksPerMachine;

        // Compute storage capacity of a rack
        double actualStorageRack= chunksPerRack/1024.0*Configuration.chunkSize; //GB
        if (actualStorageRack<=0) throw new RuntimeException("too many slices generated > 2^31");

        // Compute amount of actual information that can be stored in a rack
        // Amount of information = storage capacity * k/n * 5/6 
        double usefulStorageRack = actualStorageRack * Configuration.k /Configuration.n * 5.0/6.0; // GB

        // Compute number of racks needed to store totalActiveStorage amount of information
        double racks=Configuration.totalActiveStorage*1024*1024/usefulStorageRack;

        // Round up to nearest integer
        Configuration.rackCount=(int)racks;
        if (racks - Configuration.rackCount!=0) Configuration.rackCount++;
        if (racks <= Configuration.numChunksDiffRacks*20/10) 
            throw new Exception("Number of racks too small - adjust Configuration.numChunksDifRacks");
        // Compute resulting total number of disks
        int totalDisks= Configuration.rackCount*Configuration.machinesPerRack*Configuration.disksPerMachine;

        // Compute actual amount of information stored in the system --> this is different from
        // totalActiveStorage because of the round-up error introduced in rackCount computation
        double totalActualStorage = usefulStorageRack*Configuration.rackCount/1024; // TB  
        // Compute total slices in the system
        //  Num slices = totalActualStorage*n/k / (chunkSize*n) = totalActualStorage/(chunkSize*k)
        int totalSlices=(int)(totalActualStorage*1024.0/(Configuration.chunkSize/1024.0*Configuration.k));
        Configuration.totalSlices=totalSlices;

        // print some of these stats
        System.out.println("totalSlices=" + totalSlices+
                " diskCount="+totalDisks + " totalStorage =" + totalActualStorage +"TB " +
                "diskSize="+ Configuration.chunkSize*Configuration.chunksPerDisk/1024 +"GB ");

        // counts for unavailability, undurability
        int unAvailableCount = 0;
        int unDurableCount = 0;

        // If we are printing events to file, then annotate filename with timestamp
        if(Configuration.eventFile != null) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss");
            Date date = new Date();
            Configuration.eventFile += "-" + dateFormat.format(date) + ".txt";
            System.out.println("Events printed to: " + Configuration.eventFile);
        }
        for (int i = 0; i < count; i++) {

            Unit root = XmlParser.readFile("test.xml");
            if (Machine.failFraction!=0){ // this is a fraction of perm. failures per month
                // we update the fail fraction so that it matches the current machine failure rate 
                double rate=getMachineFailureGeneratorRate(root);
                if (rate!=-1 && rate!=0) {
                    int totalMachines=Configuration.machinesPerRack*Configuration.rackCount;
                    double allMachineFailuresPerHour=totalMachines/rate;
                    double permanentMachinesPerHour=Machine.failFraction*totalMachines/(24*30);
                    Machine.failFraction=permanentMachinesPerHour/allMachineFailuresPerHour;
                }
            }

            EventQueue events = new EventQueue();
            root.generateEvents(events, 0, Configuration.totalTime, true); //generate events

            // We either print generated events to file, or handle them
            if(Configuration.eventFile != null) { //print events to file
                events.printAll(Configuration.eventFile, "\nIteration number: "+i);
            } else { // handle events
                Configuration.print();

                EventHandler handler = new RandomDistributeEventHandler2();
                handler.start(root,totalSlices,totalDisks);
                System.err.println("Starting simulation");

                //Event handling loop
                Event e = events.removeFirst();
                while(e != null) {
                    handler.handleEvent(e, events);
                    e = events.removeFirst();
                    eventsHandled++;
                }

                Result result = handler.end();
                System.out.println(result);
                System.out.println("Events handled: "+eventsHandled);
                unAvailableCount+=result.unAvailableCount;
                unDurableCount+=result.unDurableCount;
            }
        }
        if(Configuration.eventFile == null) { //average across iterations
            System.out.println("avg unavailable="+(double)unAvailableCount/(double)count);
            System.out.println("avg undurable="+(double)unDurableCount/(double)count);
        }
    }
}
