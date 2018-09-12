#!/bin/bash


echo "Benching for $1"

sbt \
"project benchmark" \
"jmh:run -prof jmh.extras.JFR:dir=./target;flameGraphDir=../thirdparty/FlameGraph;jfrFlameGraphDir=../thirdparty/jfr-flame-graph/build/install/jfr-flame-graph/bin;flameGraphOpts=--minwidth,2;verbose=true -f1 $1"
