#!/bin/bash

[ -z "$1" ] && { echo "$0 files"; exit -1;}

declare -a statTags
declare -a statIdx
declare -a max

first=$1
[ -e $first ] || { echo "cant find files"; exit -1; }

totalSlices=`cat $1 | grep "totalSlices" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
diskCount=`cat $1 | grep "totalSlices" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$4;c++}END{print a/c}'`
totalStorage=`cat $1 | grep "totalSlices" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$6;c++}END{print a/c}'`
totalScrubs=`cat $1 | grep -w "totalScrubs" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
totalScrubRepairs=`cat $1 | grep -w "totalScrubsRepairs" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
totalDiskFailures=`cat $1 | grep -w "totalDiskFailures" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
totalTime=`cat $1 | grep -w "totalTime:" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
anomalousAvailableCount=`cat $1 | grep -w "anomalousAvailableCount" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
totalDiskFailures=`cat $1 | grep -w "totalDiskFailures" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
totalDiskRepairs=`cat $1 | grep -w "totalDiskRepairs" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
totalPermMachineFailures=`cat $1 | grep -w "totalPermMachineFailures" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
totalShortTempMachineFailures=`cat $1 | grep -w "totalShortTempMachineFailures" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
totalLongTempMachineFailures=`cat $1 | grep -w "totalLongTempMachineFailures" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
totalEagerMachineRepairs=`cat $1 | grep -w "totalEagerMachineRepairs" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
totalEagerSliceRepairs=`cat $1 | grep -w "totalEagerSliceRepairs" |  tr '=' ' '| awk 'BEGIN{a=0;c=0}{a+=$2;c++}END{print a/c}'`
totalEvents=`cat $1 | grep "Events handled" | awk 'BEGIN{a=0;c=0}{a+=$3;c++}END{print a/c}'`

echo -n "totalSlices diskCount totalStorage totalScrubs totalScrubRepairs totalDiskFailures totalTime anomalousAvailableCount totalDiskFailures totalDiskRepairs totalPermMachineFailures totalShortTempMachineFailures totalLongTempMachineFailures totalEagerMachineRepairs totalEagerSliceRepairs totalEvents "

statTags=(unAvailable unDurable Avg_durable_degraded__per_24h_slices Avg_available_degraded__per_24h_slices Avg_bandwidth__per_24h_GBPerday)
statIdx=(2 4 2 2 2)
max=(0 0 6 6 6)
for i in `seq 0 4`; do
	echo -n " ${statTags[i]} max stdev "
done
echo "count"

echo -n "$totalSlices $diskCount $totalStorage $totalScrubs $totalScrubRepairs $totalDiskFailures $totalTime $anomalousAvailableCount $totalDiskFailures $totalDiskRepairs $totalPermMachineFailures $totalShortTempMachineFailures $totalLongTempMachineFailures $totalEagerMachineRepairs $totalEagerSliceRepairs $totalEvents "

for i in `seq 0 4`; do
	mean=(`cat $@  | tr '=' ' ' |tr ':' ' ' | awk "BEGIN{max=0;s=0; c=0; i=0;mm=0;}{if (/${statTags[$i]}/) {if(mm==0) { c++; p=${statIdx[$i]};s+=\\$p ; if(${max[$i]}>0) { p=${max[$i]}; max+=\\$p;  } }; mm=0;}  if (/totalMachineRepairs/) tmf=\\$2; if (/totalMachineRepairs/) { tmr=\\$2; if (tmf!=tmr) { mm=1;} }}END{print s/c\\" \\"max/c;}"`)

	echo -n " ${mean[0]} ${mean[1]} "

	stdev=(`cat $@ | tr '=' ' ' | awk "BEGIN{s=0; c=0; i=0;mm=0;}{if (/${statTags[$i]}/) {if(mm==0) { c++;p=${statIdx[$i]}; s+=(\\$p-${mean[0]})^2}; mm=0;}  if (/totalMachineFailures/) tmf=\\$2; if (/totalMachineRepairs/) { tmr=\\$2; if (tmf!=tmr) { mm=1; } }}END{print sqrt(s)/(c-1); print c}"`)
	echo -n ${stdev[0]} " " 
done
echo ${stdev[1]}
#
#mean=`cat $@  |  awk 'BEGIN{s=0; c=0; i=0;mm=0;}{if (/Avg_durable_degraded__per_24h_slices/) {if(mm==0) { c++;s+=$2}; mm=0;}  if (/totalMachineFailures/) tmf=$2; if (/totalMachineRepairs/) { tmr=$2; if (tmf!=tmr) { mm=1;} }}END{print s/c}'`
#echo $mean





