#!/bin/sh -

set -ex

DATA="$PWD"
BUSYBOX="$PWD/files/xbin/busybox"

PACKAGE="com.manuelnaranjo.btle.installer2"
PROGRESS="com.manuelnaranjo.btle.installer2.PROGRESS"
COMPLETE="com.manuelnaranjo.btle.installer2.COMPLETE"
ANDROID_BT="/system/etc/permissions/android.hardware.bluetooth_le.xml"

BOARD=`getprop ro.hardware`
BUILD=`getprop ro.build.id | ${BUSYBOX} tr '[:upper:]' '[:lower:]'`
RELEASE=`getprop ro.build.version.release`


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

# mount /system as RW
$BUSYBOX mount -rw -o remount /system

broadcastProgress "/system mounted as RW"

broadcastProgress "Doing installation on a $BOARD device"

# get into the right directory based on our target
cd $BOARD

broadcastProgress "Restoring build number ${BUILD}"

cd ${BOARD}-${BUILD}

for i in `$BUSYBOX find -type f`; do
    TARGETDIR="/system/$($BUSYBOX dirname ${i})/"
    FILE="$($BUSYBOX basename ${i})"
    broadcastProgress "Installing ${i} $TARGETDIR/$FILE"
    $BUSYBOX cp ${i} ${TARGETDIR}
    broadcastProgress "Setting proper permissions"
    chmod 755 ${TARGETDIR}/${FILE}
done

if [ -f ${ANDROID_BT} ]; then
    broadcastProgress "Removing ${ANDROID_BT}"
    rm ${ANDROID_BT}
fi

$BUSYBOX sync

# remount as ro
# $BUSYBOX mount -r -o remount /system
# broadcastProgress "/system mounted as RO"

broadcastProgress "Completed installation"

broadcastComplete "true"

trap - EXIT
