#!/bin/bash

trap "killall java" SIGTERM
set -x 

pid=$1
[ -z "$pid" ] && { echo "PID expected as a param"; exit -1; }

for scrub in 1000 1000000 ; do
for recovery in 10; do
	echo recovery_scrub $recovery  $scrub >> ${pid}.log_r=${recovery}_s=${scrub}

	cat test.xml.template | sed "s/LATENT_RECOVERY_RATE/$scrub/" | sed "s/DISK_RECOVERY_RATE/$recovery/" > test.xml

	cat test.xml >> ${pid}.log_r=${recovery}_s=${scrub}
	java -Xmx1G -jar simulator.jar 1 3 1 3 3 20 11 >> ${pid}.log_r=${recovery}_s=${scrub} 
done
done
