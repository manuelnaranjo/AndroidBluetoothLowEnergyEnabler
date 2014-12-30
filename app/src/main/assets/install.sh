#!/bin/sh -

DATA="$PWD"
BUSYBOX="$PWD/xbin/busybox"

echo $BUSYBOX

PACKAGE="com.manuelnaranjo.btle.installer2"
PROGRESS="com.manuelnaranjo.btle.installer2.PROGRESS"
COMPLETE="com.manuelnaranjo.btle.installer2.COMPLETE"

BOARD=`getprop ro.hardware`
BUILD=`getprop ro.build.id`
RELEASE=`getprop ro.build.version.release`

set -- ${RELEASE//./ }
MAJOR="$1"
MINOR="$2"
MAINTENANCE="$3"


broadcastProgress ()
{
    am broadcast -a $PROGRESS \
        --es "DATA" "$1" \
        --selector $PACKAGE \
        --include-stopped-resolution
}

broadcastComplete()
{
    am broadcast -a $COMPLETE \
        --ez "DATA" "$1" \
        --selector $PACKAGE \
        --include-stopped-resolution
}

cleanup() {
    echo "cleanup"
    broadcastComplete "false"
}

set -e

trap cleanup EXIT

# mount /system as RW
mount -rw -o remount /system

broadcastProgress "/system mounted as RW"

broadcastProgress "Doing installation on a $BOARD device"

# get into the right directory based on our target
cd $BOARD

if [ $MAJOR -ge "5" ]; then
    broadcastProgress "Installing 5.X version"
    cd ble-5.0
else
    broadcastProgress "Installing 4.X version"
    cd ble-4.0
fi

for i in `$BUSYBOX find -type f`; do
    TARGETDIR="/system/$($BUSYBOX dirname ${i})/"
    FILE="$($BUSYBOX basename ${i})"
    broadcastProgress "Installing ${i} $TARGETDIR/$FILE"
    $BUSYBOX cp ${i} ${TARGETDIR}
    broadcastProgress "Setting proper permissions"
    chmod 755 ${TARGETDIR}/${FILE}
done

# get out now
cd $DATA

# remount as ro
mount -r -o remount /system

broadcastProgress "/system mounted as RO"

broadcastProgress "Completed installation"

broadcastComplete "true"

trap - EXIT
