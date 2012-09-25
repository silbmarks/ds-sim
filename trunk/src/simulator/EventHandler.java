/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator;

public interface EventHandler {
    public void start(Unit root, int totalSlices, int diskCount);
    public void handleEvent(Event e, EventQueue queue);
    public Result end();
}
