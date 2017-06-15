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

# leader host for startup purposes only
# (once running, all nodes are the same -- no leaders)
STARTUPLEADERHOST="localhost"
# list of cluster nodes separated by commas in host:[port] format
SERVERS="localhost"

# remove binaries, logs, runtime artifacts, etc... but keep the jars
function clean() {
    rm -rf voltdbroot log procedures/volttable_segv/*.class client/volttable_segv/*.class *.log
}

# remove everything from "clean" as well as the jarfiles
function cleanall() {
    clean
    rm -rf volttable_segv-procs.jar volttable_segv-client.jar
}

# compile the source code for procedures and the client into jarfiles
function jars() {
    # compile java source
    javac -classpath $APPCLASSPATH procedures/volttable_segv/*.java
    javac -classpath $CLIENTCLASSPATH client/volttable_segv/*.java
    # build procedure and client jars
    jar cf volttable_segv-procs.jar -C procedures volttable_segv
    jar cf volttable_segv-client.jar -C client volttable_segv
    # remove compiled .class files
    rm -rf procedures/volttable_segv/*.class client/volttable_segv/*.class
}

# compile the procedure and client jarfiles if they don't exist
function jars-ifneeded() {
    if [ ! -e volttable_segv-procs.jar ] || [ ! -e volttable_segv-client.jar ]; then
        jars;
    fi
}

# Init to directory voltdbroot
function voltinit-ifneeded() {
    voltdb init --force
}

# run the voltdb server locally
function server() {
    jars-ifneeded
    voltinit-ifneeded
    voltdb start -H $STARTUPLEADERHOST
}

# load schema and procedures
function init() {
    jars-ifneeded
    sqlcmd < ddl.sql
}

# run the client that drives the example
function client() {
    async-benchmark
}

# Asynchronous benchmark sample
# Use this target for argument help
function async-benchmark-help() {
    jars-ifneeded
    java -classpath volttable_segv-client.jar:$CLIENTCLASSPATH volttable_segv.AsyncBenchmark --help
}

# latencyreport: default is OFF
# ratelimit: must be a reasonable value if lantencyreport is ON
# Disable the comments to get latency report
function async-benchmark() {
    jars-ifneeded
    java -classpath volttable_segv-client.jar:$CLIENTCLASSPATH volttable_segv.TheApp \
        --displayinterval=5 \
        --warmup=5 \
        --duration=120 \
        --servers=$SERVERS

}

# trivial client code for illustration purposes
function simple-benchmark() {
    jars-ifneeded
    java -classpath volttable_segv-client.jar:$CLIENTCLASSPATH -Dlog4j.configuration=file://$LOG4J \
        volttable_segv.SimpleBenchmark $SERVERS
}

# Multi-threaded synchronous benchmark sample
# Use this target for argument help
function sync-benchmark-help() {
    jars-ifneeded
    java -classpath volttable_segv-client.jar:$CLIENTCLASSPATH volttable_segv.SyncBenchmark --help
}

function sync-benchmark() {
    jars-ifneeded
    java -classpath volttable_segv-client.jar:$CLIENTCLASSPATH -Dlog4j.configuration=file://$LOG4J \
        volttable_segv.SyncBenchmark \
        --displayinterval=5 \
        --warmup=5 \
        --duration=120 \
        --servers=$SERVERS \
        --contestants=6 \
        --maxvotes=2 \
        --threads=40
}

# JDBC benchmark sample
# Use this target for argument help
function jdbc-benchmark-help() {
    jars-ifneeded
    java -classpath volttable_segv-client.jar:$CLIENTCLASSPATH volttable_segv.JDBCBenchmark --help
}

function jdbc-benchmark() {
    jars-ifneeded
    java -classpath volttable_segv-client.jar:$CLIENTCLASSPATH -Dlog4j.configuration=file://$LOG4J \
        volttable_segv.JDBCBenchmark \
        --displayinterval=5 \
        --duration=120 \
        --servers=$SERVERS \
        --maxvotes=2 \
        --contestants=6 \
        --threads=40
}

function help() {
    echo "Usage: ./run.sh {clean|cleanall|jars|server|init|client|async-benchmark|aysnc-benchmark-help|...}"
    echo "       {...|sync-benchmark|sync-benchmark-help|jdbc-benchmark|jdbc-benchmark-help|simple-benchmark}"
}

# Run the targets pass on the command line
# If no first arg, run server
if [ $# -eq 0 ]; then server; exit; fi
for arg in "$@"
do
    echo "${0}: Performing $arg..."
    $arg
done
