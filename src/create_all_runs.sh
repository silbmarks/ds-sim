#!/bin/bash
## 3w-rep, (14,10), (16,10), (10,5)
## (14,12,10), (15,12,10), (10,7,5)
## scrubb = 1000, scrub = 0
## recovery rate: 2m per disk, 2h per disk
## permanent failure rate: 0.008, 0.0008 - per month
## disks_per_machine: 4  20
## machines_per_rack: 10 40
set -x
############=================
#n of the encoding
declare -a ns
#k of the encoding
declare -a ks
#thresholds
declare -a ths
#scrub_freq - hours
declare -a scrub_freqs
#recovery rate blocks per hour
declare -a recovery_rates
#disks per machines
declare -a disks_per_machine
declare -a fail_fraction

declare -a machines_per_rack
declare -a simulation_iterations


ns=(3 14 15 14)
ks=(1 10 10 10)
ths=(2 13 12 12)
simulation_iterations=(1 1 1 1)

scrub_freqs=(1000)
#recovery_rates=(4500 450)
disks_per_machine=(20)
machines_per_rack=(11)
fail_fraction=("0.008")


die(){
	echo "Error $1";
	exit -1;
}

mkdir_my(){
	if [ -e $1 ]; then 
		mv $1 $1.old || die "Cant move dir"
	fi
	mkdir $1 || die "Cant create dir"
	echo Dir $1 created;
}
	
	
	

now=`date +%D_%T | tr '/' '_' | tr ':' '_'`

echo $now

mkdir_my $now 
cd $now || die "Cannot chdir to $now"

let total_params="${#ns[*]}-1"
for params in `seq 0 $total_params`; do
	exp_dir=${ns[$params]}_${ks[$params]}_${ths[$params]}
	mkdir_my $exp_dir
	cd $exp_dir || die "Cannot chdir to $exp_dir"
	for scrub in ${scrub_freqs[*]}; do
#		for rec_rate in ${recovery_rates[*]}; do
			for disks in ${disks_per_machine[*]}; do
				for machPerRack in ${machines_per_rack[*]}; do
					for fail_frac in ${fail_fraction[*]}; do

						work_dir=${scrub}_${rec_rate}_${disks}_${machPerRack}_${fail_frac}
#						disk_rr=`echo 3000/$rec_rate | bc -l`
						mkdir_my $work_dir
						cd $work_dir || die "Cannot chdir to $work_dir"
						cp -a ../../../condor_submission_scripts/* . || die "Cannot copy scripts"
						cat run_test.sh.template | sed "s/N_PARAM/${ns[$params]}/" | sed "s/K_PARAM/${ks[$params]}/" | sed "s/T_PARAM/${ths[$params]}/" | sed "s/DISKS/$disks/" | sed "s/MACHINES/$machPerRack/" | sed "s/ITERATIONS/${simulation_iterations[$params]}/" > run_test.sh
						cat test.xml.template | sed "s/MACHINE_RECOVERY_RATE/$rec_rate/" | sed "s/SCRUB_RATE/$scrub/" | sed "s/FAIL_FRAC/$fail_frac/" | sed "s/DISK_RECOVERY_RATE/$disk_rr/" > test.xml
						cd .. || die "cannot get back"
					done
				done
			done
#		done
	done
	cd .. || die "Cannot get back"
done
