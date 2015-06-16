#!/bin/sh -

set -x

DATA="$PWD"
BUSYBOX="$PWD/files/xbin/busybox"

PACKAGE="com.manuelnaranjo.btle.installer2"
PROGRESS="com.manuelnaranjo.btle.installer2.PROGRESS"
COMPLETE="com.manuelnaranjo.btle.installer2.COMPLETE"

echo $PWD

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

trap cleanup EXIT

cd files

if [ -w /system/ ]; then
    # mount /system as RW
    mount -rw -o remount /system

    broadcastProgress "/system mounted as RW"
else
    broadcastProgress "/system was mounted as RW, skipping test"
fi

set -e

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

# first remove original files
for i in `$BUSYBOX find -type f`; do
    TARGETDIR="/system/$($BUSYBOX dirname ${i})/"
    FILE="$($BUSYBOX basename ${i})"
    broadcastProgress "Removing $TARGETDIR/$FILE"
    ${BUSYBOX} rm -f ${TARGETDIR}/${FILE}
done

# now handle the installation per se
for i in `$BUSYBOX find -type f`; do
    TARGETDIR="/system/$($BUSYBOX dirname ${i})/"
    FILE="$($BUSYBOX basename ${i})"
    broadcastProgress "Installing ${i} $TARGETDIR/$FILE"
    $BUSYBOX cp ${i} ${TARGETDIR}
    broadcastProgress "Setting proper permissions"
    chmod 644 ${TARGETDIR}/${FILE}
done

# get out now
cd $DATA

set +e
# remount as ro
mount -r -o remount /system
broadcastProgress "/system mounted as RO"

broadcastProgress "Completed installation"

broadcastComplete "true"

trap - EXIT
