/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.failure;

public class GFSAvailability extends Piecewise {
	// recovery interval	
	static double availTable[]= {0.1, 0.25, 0.5, 1, 6, 24, 144};
	// probability distribution. 
	static double freqTable[]=  {0, 0.91, 0.083, 0.0047, 0.001, 0.00075, 0.00060, 1};
	
	public GFSAvailability() {
		super(freqTable,availTable);
	}
}
