#!/bin/bash


die(){
	echo $1
	exit -1;
}
set -x
cwd=`pwd`
prio_ctr=10
alldirs=`find -mindepth 2 -type d`;
for i in $alldirs; do
	cd $i || die "cannot chdir"
	now=`date +%s`
	[ -e create_dag.sh ] || die "cant find create_dag.sh"
	./create_dag.sh 5000 0 > dag5000.dag || die "Failed to create dag"
	cat javasub_dag.sub.template |sed "s/__PRIO__/$prio_ctr/" | sed "s/__LOG__/${now}_${prio_ctr}/" > javasub_dag.sub || die "Failed to create dag.sub"
	condor_submit_dag -maxjobs 500 dag5000.dag || die "Failed to submit dag"
	let prio_ctr="$prio_ctr+1"
	cd $cwd || die "Cannot return back"
done
	

	
	
