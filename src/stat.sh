#!/bin/bash

for i in `find -mindepth 2 -maxdepth 2 -type d`; do echo $i; ../avail.sh $i/*.log; done
	
