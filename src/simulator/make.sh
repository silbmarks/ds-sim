#!/bin/bash

if [ $# -gt 0 ];
then
    if [ "$1" != "clean" -a "$1" != "CLEAN" ]; then
        echo "Usage: $0 [clean]"
        exit
    fi
    for file in `find . -name "*.class"`; 
    do
        rm -f $file
    done
    exit
fi

find . -name "*.java" > sourcelist
javac @sourcelist
rm sourcelist
