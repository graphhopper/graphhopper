#!/bin/bash
# Get current swap usage for all running processes
# Erik Ljungstrom 27/05/2011
SUM=0
OVERALL=0
for DIR in `find /proc/ -maxdepth 1 -type d | egrep "^/proc/[0-9]"` ; do
PID=`echo $DIR | cut -d / -f 3`
#PROGNAME=`ps -p $PID -F --no-headers`
PROGNAME=`ps -p $PID -o comm --no-headers`
for SWAP in `grep Swap $DIR/smaps 2>/dev/null| awk '{ print $2 }'`
do
let SUM=$SUM+$SWAP
done
echo "PID=$PID - Swap used: $SUM from: $PROGNAME"
let OVERALL=$OVERALL+$SUM
SUM=0

done
echo "Overall swap used: $OVERALL"