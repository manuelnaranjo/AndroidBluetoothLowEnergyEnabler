#!/system/bin/sh

export CLASSPATH="/system/framework/btle-framework.jar"
	
while [ ! -e ${CLASSPATH} ]; do
	echo "Waiting for /system/framework to be ready"
	sleep 0.1
done

while [ ! -e /dev/socket/dbus ]; do
	echo "waiting for dbus socket"
	sleep 0.1
done

while [ -z "$(ps | grep system_server)" ]; do
	echo "waiting for system server"
	sleep 1
done

while [ 1 ]; do 
	echo "Launching btle-framework"
	/system/bin/app_process \
		/system/bin \
        		--nice-name=btle-framework \
                	android.bluetooth.le.server.Main $@
	if [ $? -eq 0 ]; then
		exit 0
	fi
done
