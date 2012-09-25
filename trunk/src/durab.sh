#!/bin/bash

[ -z "$1" ] && { echo "$0 files"; exit -1;}

mean=`cat $@ | awk 'BEGIN{s=0; c=0; i=0;}{if (/totalSlices/) {s+=c;c=0;i++;} if (/ durCount:/) c++; }END{print s/i}'`

echo $mean

stdev=`cat $@ | awk "BEGIN{s=0; c=0; i=0;}{if (/totalSlices/) {s+=(c-$mean)^2;c=0;i++;} if (/ durCount:/) c++; }END{print sqrt(s)/(i-1)}"`

echo $stdev
