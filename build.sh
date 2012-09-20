#!/bin/bash

set -e

if [ -z "${1}" ]; then
    echo "Usage ${0} <path to AOSP tree>"
    exit 1
fi

AOSP=${1}

GATT=${AOSP}/btle/framework/java/src/android/bluetooth/le/server/BluetoothGatt.java
API_VERSION=$( grep API_LEVEL ${GATT} \
    | grep int | awk -F\= '{ print $2}'  | awk -F\; '{ print $1}' )
FRAMEWORK_VERSION=$( grep FRAMEWORK_VERSION ${GATT} \
    | grep String | awk -F\= '{ print $2}'  | awk -F\" '{ print $2}' )

echo "Updating framework $FRAMEWORK_VERSION, api version $API_VERSION"

pushd ${AOSP}
source build/envsetup.sh
lunch full_maguro-userdebug
make -j8 clean-btle-api clean-btle-framework
make -j8 btle-api btle-framework
popd

cp ${AOSP}/out/target/product/maguro/system/framework/btle-framework.jar \
    res/raw/btle_framework.jar

rm -f libs/btle/framework-*.jar

cp ${AOSP}/out/target/common/obj/JAVA_LIBRARIES/btle-api_intermediates/javalib.jar \
    libs/btle-framework-${FRAMEWORK_VERSION}.jar

sed -s "s/_API_VERSION_/${API_VERSION}/g" strings.xml | \
    sed "s/_FRAMEWORK_VERSION_/${FRAMEWORK_VERSION}/g" \
    > res/values/strings.xml

exec ant debug
