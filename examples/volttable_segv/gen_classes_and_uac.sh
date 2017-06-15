#!/usr/bin/env bash

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

if [ $# -eq 0 ]; then
    echo "Please supply numbers file name"
    exit 1
fi

rm -f procedures/volttable_segv/BusinessLogic*
rm -f procedures/volttable_segv/CommonProc.java
./generate_classes.py ${1}

javac -classpath $APPCLASSPATH procedures/volttable_segv/*.java
jar cf volttable_segv-procs.jar -C procedures volttable_segv

echo 'load classes volttable_segv-procs.jar;' | sqlcmd
