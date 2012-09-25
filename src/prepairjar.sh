#!/bin/bash
find simulator -name '*.java' > make
javac @make &&  jar cvfe simulator.jar simulator.Test simulator/
cp -v simulator.jar condor_submission_scripts/
