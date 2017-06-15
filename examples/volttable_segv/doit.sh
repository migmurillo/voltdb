#!/bin/bash

# find voltdb binaries
if [ -e ../../bin/voltdb ]; then
    # assume this is the examples folder for a kit
    VOLTDB_BIN="$(dirname $(dirname $(pwd)))/bin"
elif [ -n "$(which voltdb 2> /dev/null)" ]; then
    # assume we're using voltdb from the path
    VOLTDB_BIN=$(dirname "$(which voltdb)")
else
    echo "Unable to find VoltDB installation."
    echo "Please add VoltDB's bin directory to your path."
    exit -1
fi

# call script to set up paths, including
# java classpaths and binary paths
source $VOLTDB_BIN/voltenv

# kill any existing voltdb server
# and reset to pre-run state
kill `jps | grep VoltDB | sed 's/VoltDB//'` || echo "No VoltDB running"
rm -f procedures/volttable_segv/BusinessLogic*
rm -f procedures/volttable_segv/CommonProc.java
rm -f numbers.txt
./run.sh clean

# generate the initial set of Java procedure classes
echo 12345 > numbers.txt
./generate_classes.py ./numbers.txt

# Generate .class files and pack into a jar
./run.sh jars

echo "Starting VoltDB..."
voltdb init --force && voltdb start &
sleep 20

# Load schema and create procedures
./run.sh init


./run.sh client
