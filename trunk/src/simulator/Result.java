/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator;

public class Result {
    public int unAvailableCount = 0;
    public int unDurableCount = 0;

    public String toString(){
        return "unAvailable="+unAvailableCount+" unDurable="+unDurableCount;
    }
}
