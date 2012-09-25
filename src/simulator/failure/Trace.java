/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.failure;
import simulator.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

public class Trace implements EventGenerator{

    public class TmpEvent{
        public int id;
        public double ts;
        public boolean start;
        public boolean perm;

        TmpEvent(int id,double ts, boolean start, boolean perm){
            this.id=id; this.ts=ts;this.start=start;this.perm=perm;
        }

        public String toString(){
            StringBuffer sb=new StringBuffer();
            sb.append(id);
            sb.append(" ");
            sb.append(ts);
            sb.append(" ");
            sb.append(start);
            sb.append(" ");
            sb.append(perm);
            sb.append(" ");
            return sb.toString();
        }
    }

    String name;
    String filename;
    static HashMap<Integer, LinkedList<TmpEvent>> events=null;

    static LinkedList<TmpEvent> currentMachine=null;
    static boolean currentEventType=false; 

    private TmpEvent[] parseLine(String line){
        String[] substrings=line.split(",");
        TmpEvent[] pair=new TmpEvent[2];

        pair[0]=new TmpEvent(Integer.parseInt(substrings[2]),
                Double.parseDouble(substrings[3])/3600,
                true,
                substrings[6].equals("permanent"));
        pair[1]=new TmpEvent(Integer.parseInt(substrings[2]),
                Double.parseDouble(substrings[4])/3600,
                false,
                substrings[6].equals("permanent"));
        return pair;
    }

    private void initEvents(String filename) throws IOException{
        if (events==null) {

            events=new  HashMap<Integer, LinkedList<TmpEvent>>();
            FileReader f=new FileReader(filename);
            if (f==null) throw new RuntimeException("Failed to open file "+ filename);
            BufferedReader br=new BufferedReader(f);

            String line;
            boolean begin=true;
            double firstEvent=Double.MAX_VALUE;
            double lastEvent=0;
            while((line=br.readLine())!=null){

                TmpEvent te[]=parseLine(line);
                if (firstEvent>te[0].ts) firstEvent=te[0].ts;
                if (lastEvent<te[1].ts) lastEvent=te[1].ts;

                LinkedList<TmpEvent> machine;

                if (!events.containsKey(te[0].id)){
                    machine=new LinkedList<TmpEvent>();
                    events.put(te[0].id,machine);
                }else{
                    machine=events.get(te[0].id);
                }

                machine.add(te[0]);
                machine.add(te[1]);
            }

            for(LinkedList<TmpEvent> m: events.values()){
                for(TmpEvent te:m){
                    te.ts-=firstEvent;
                }
            }

            if (Configuration.totalTime > (lastEvent-firstEvent) ) {
                System.err.println("WARNING: Requested simulation time is LARGER than the trace time: "+ Configuration.totalTime + " days > " + (lastEvent-firstEvent) +" days");
                System.err.println("Setting simulation time to the trace time");
                Configuration.totalTime =(long)(lastEvent-firstEvent);
            }
        }
    }

    public void setCurrentMachine(int id){
        currentMachine=null;
        if (!events.containsKey(id)) {
            System.err.println("No events found for machine "+id);
        }
        currentMachine=events.get(id);
    }

    public void setCurrentEventType(boolean start){
        currentEventType=start;
    }

    @Override
        public void init(String name, HashMap<String, String> parameters) {
            this.name=name;
            this.filename=parameters.get("filename");
            try{
                initEvents(filename);
            }catch(Exception e){
                System.err.println("Failed to read file: "+e);
                throw new RuntimeException(e);
            }
        }

    public void eventAccepted(){
        currentMachine.pop();
    }

    @Override
        public double generateNextEvent(double currentTime) {
            if (currentMachine==null) return Double.MAX_VALUE;

            if (currentMachine.isEmpty()) {
                System.err.println("No more events for machine "+currentMachine);
                return Double.MAX_VALUE;
            }

            TmpEvent event=currentMachine.peekFirst();
            assert(event.start == currentEventType);
            assert(currentTime <= event.ts);
            return event.ts;
        }

    @Override
        public void reset(double currentTime) {
        }

    @Override
        public String getName() {
            return name;
        }

    @Override
        public double getCurrentTime() {
            return 0;
        }
}
