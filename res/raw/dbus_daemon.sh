#!/system/bin/sh

export CLASSPATH="/system/framework/btle-framework.jar"
/system/bin/logwrapper \
	/system/bin/app_process \
		/system/bin \
                --nice-name=btle-framework \
                android.bluetooth.le.server.Main &

/system/bin/dbus-daemon.orig $@
