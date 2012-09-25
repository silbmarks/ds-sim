/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package raid;

public interface EventGenerator {
	public void reset(double currentTime);
	public double nextEventTime(double currentTime);
}
