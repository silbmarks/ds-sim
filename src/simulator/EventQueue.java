/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator;
import java.util.*;
import java.io.*;

public class EventQueue {
    private TreeMap<Double, LinkedList<Event>> events = new TreeMap<Double, LinkedList<Event>>();

    public void addEvent(Event e){
        if(events.containsKey(e.getTime()))
            events.get(e.getTime()).add(e);
        else{
            LinkedList<Event> list = new LinkedList<Event>();
            list.add(e);
            events.put(e.getTime(), list);
        }
    }

    public void addEventQueue(EventQueue queue){
        for(Event e:queue.getAllEvents()){
            addEvent(e);
        }
    }

    public Event removeFirst() {
        if(events.firstEntry() == null)
            return null;
        Double firstKey = events.firstEntry().getKey();
        LinkedList<Event> firstValue = events.firstEntry().getValue();
        Event firstEvent = firstValue.removeFirst();
        if(firstValue.size()==0) events.remove(firstKey);
        return firstEvent;
    }

    public LinkedList<Event> getAllEvents(){
        LinkedList<Event> ret = new LinkedList<Event>();
        for(LinkedList<Event> tmp:events.values())
            ret.addAll(tmp);
        return ret;
    }

    public Event[] convertToArray(){
        int size = this.size();
        Event[] ret = new Event[size];
        Iterator<LinkedList<Event>> iterator = events.values().iterator();
        int index=0;
        LinkedList<Event> l;
        while(iterator.hasNext()){
            l=iterator.next();
            for(Event e:l){
                ret[index++]=e;
            }
            iterator.remove();
        }
        return ret;
    }

    @Override
        public EventQueue clone(){
            EventQueue ret = new EventQueue();
            for(Double time:this.events.keySet()){
                LinkedList<Event> list = this.events.get(time);
                LinkedList<Event> list2 = new LinkedList<Event>();
                for(Event e:list)
                    list2.add(e);
                ret.events.put(time, list2);
            }
            return ret;
        }

    public int size(){
        int size=0;
        for(LinkedList<Event> list:events.values())
            size+=list.size();
        return size;
    }

    public void printAll(String file, String msg){
        try {
            PrintWriter out = new PrintWriter(new FileWriter(new File(file), true)); // can append to file
            out.println(msg);
            for(Double time:this.events.keySet()){
                LinkedList<Event> list = this.events.get(time);
                for(Event e:list) {
                    if(e.ignore == false)
                        out.println(e);
                }
            }
            out.close();
        } catch(IOException e) {
            System.out.println("Unable to print generated events to file: " + e.getMessage());
            return;
        }
    }

}
