/******************************************************
 * Distributed Storage Simulator 0.1
 * by Lakshmi Ganesh, Mark Silberstein, and Yang Wang
 * see https://code.google.com/p/ds-sim/
 ******************************************************/
package simulator.failure;

public class GFSAvailability2 extends Piecewise {
	// recovery interval	
	static double availTable[]= {0.1, 0.16, 0.66,1.6, 6.6,   16,  32,   48,  166};
	// probability distribution. 
	static double freqTable[]=  {0,   0.6, 0.2, 0.05, 0.05, 0.02,0.07, 0.001,1};
	//									60% 80% 85%   90%   92% 99%    99.9%
	public GFSAvailability2() {
		super(freqTable,availTable);
	}
}
