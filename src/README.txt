The number of parameters controlling the simulator is starting to become unwieldy, so here's a quick tutorial on some of them:

Lazy Recovery
=============

Definition: When recovery of slices is conditional on some threshold of their chunks being 'lost' (the definition of lost is also an input -- it can be only durability failures, or might also include availability failures).

Parameters:
    - recoveryThreshold (set in Configuration.java): If <= this number of chunks remains for a slice, then the slice is recovered.
    - lazyRecovery (set in Configuration.java): If set to true, then during a slice recovery, ALL its 'lost' chunks are recovered; if set to false, then only that chunk is recovered that is on the current disk being recovered.
    
Example: 
1. In (15,10) encoding, if recoveryThreshold is set to 12, and lazyRecovery is set to true, then during each disk recovery, each slice is examined to see if it satisfies the following condition: (number of remaining chunks of this slice <= 12). If this condition is true, then all 'lost' chunks of this slice are recovered.
2. In (3,1) encoding, if recoveryThreshold is set to 2, and lazyRecovery is set to false, then it basically corresponds to 3-way replication without lazy recovery. At each disk recovery, each slice is examined to see if (number of remaining chunks of this slice <= 2) -- that is, if any chunk was lost. If so, then the chunk on this disk is recovered (but not any other chunks that may also be 'lost').

Eager Recovery
==============

Definition: Disk recovery is initiated for temporary machine failures that exceed a specified timeout.

Parameters:
    eagerRecoveryEnabled (set in test.xml): If set to true, then eager recovery is enabled.
    failTimeout (set in test.xml): If a temporary machine failure duration exceeds this timeout, then an eager recovery event is triggered.
    availabilityCountsForRecovery (set in Configuration.java): If set to true, then unavailable chunks (that is, ones whose machines have suffered temporary failures of greater than failTimeout duration) are also considered 'lost'; that is, lazy recovery will be triggered by these events as well, and will also recover these chunks.

Example:
1. If eagerRecoveryEnabled is false, then temporary machine failures will never trigger disk recovery, no matter how long they last.
2. If eagerRecoveryEnabled=true, failTimeout=0.25, availabilityCountsForRecovery=false: If machine A fails at time t, and has not recovered by time t+0.25, then each of its disks undergoes eager recovery. During eager disk recovery, each slice is examined to see if <= recoveryThreshold number of chunks remain. Here, chunks are considered lost only if they have suffered durability failures -- that is, their disk has failed, or they have suffered a latent error. If the threshold has been crossed, then the slice is recovered. Note that only undurable chunks are recovered.
3. If eagerRecoveryEnabled=true, failTimeout=0.25, availabilityCountsForRecovery=true: If machine A fails at time t, and has not recovered by time t+0.25, then each of its disks undergoes eager recovery. During eager disk recovery, each slice is examined to see if <= recoveryThreshold number of chunks remain. Here, chunks are considered lost if (a) their disk has failed, or (b) they have suffered a latent error, or (c) their machine has undergone a temporary failure of duration greater than failTimeout. If the threshold has been crossed, then the slice is recovered. Note that not just undurable, but also unavailable chunks are recovered.

Important note: the parameter availabilityCountsForRecovery (in Configuration.java) controls not just eager recovery, but also regular recovery. During *any* recovery (whether eager or regular), if availabilityCountForRecovery=true, then a chunk is considered 'lost' if (a) its disk has failed, or (b) it has suffered a latent error, or (c) its machine has undergone a temporary failure of greater than failTimeout duration. Also, during slice recovery, both undurable (that is, disk failure/latent error) as well as unavailable (that is, machine failure > failTimeout) chunks are recovered.


