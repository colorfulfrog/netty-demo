#!/bin/bash
echo 'start ios tcpserver...'
java -jar yx-ios-tcpserver.jar start &

sleep 3
echo 'start android tcpserver...'
java -jar yx-android-tcpserver.jar start &
#log file = /alidata1/logs/yxhl-tcp.log