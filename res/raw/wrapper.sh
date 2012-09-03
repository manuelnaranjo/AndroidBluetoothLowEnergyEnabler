#!/system/bin/sh

/system/bin/logwrapper /system/bin/btle-framework --no-daemon &

exec /system/bin/$(basename $0).orig $@
