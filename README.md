# Introduction

Bluetooth Low Energy Enabler for Nexus 7 and 10 installer

This is the project I use to generate:
* https://play.google.com/store/apps/details?id=com.manuelnaranjo.btle.installer2

In order to get a full build you will need an Android AOSP checkout and some
patches, this will generate the installer and may reflect the last available
binaries.

# Building instructions

1 - Init repo as AOSP instructions says
2 - Copy mnaranjo.xml and busybox.xml into <AOSP>/.repo/local_manifests.xml
3 - Run:

    $ repo sync

4 - Merge projects listed in mnaranjo.xml with upstream ones
5 - Run:

    $ source build/envsetup.sh
    $ launch aosp_<model>-userdebug
    $ make busybox bluetooth.default -j 4
    <copy files into app/src/main/assets/ >

# Packing

## Debug APK

    <update AndroidManifest.xml>
    ./gradlew assembleDebug

## Release APK

Once you got the debug version tested and running you need to create
app/btle.properties with the following structure:

    keystore=<path to your keystore>
    keystore.password=<keystore password>
    keystore.alias=<keystore alias>
    key.password=<password for the key to use>

Then you can build the release ready apk with

    ./gradlew assembleRelease

# Old instructions

## BusyBox

You need to build busybox in order to get the installer working, once build you
should update app/src/main/assets/xbin/busybox, instructions for building are:

    git clone https://github.com/sherpya/android-busybox.git
    cd android-busybox
    cp <BTLE-checkout>/busybox/config .config
    patch -p1 < <BTLE-checkout>/busybox/Makefile.lib.patch
    make menuconfig # update your ndk paths, add/remove applets
    make all V=1
    cp busybox <BTLE-checkout>/app/src/main/assets/xbin

## AOSP part

Short instructions on how to build the binaries

    source build/envsetup.sh
    lunch aosp_<model>-userdebug
    modify devices/<maker>/<model>/bluetooth/bluedroid_builcfg.h
    make bluetooth.default -j 4
    <copy files>
