#!/bin/bash

[ -z "$2" ] && { echo "<number of iterations> <offset>"; exit -1; }

iters=$1
offset=$2
let iters="$iters+$offset"

for i in `seq $offset $iters`; do cat dag.one | sed "s/NAME/JOB_${i}/" | sed "s/ITER/$i/"  ;done
