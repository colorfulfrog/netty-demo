#!/bin/bash
echo 'stop ios tcpserver...'
java -jar yx-ios-tcpserver.jar stop
sleep 3
jps -mlvV | grep 'yx-ios-tcpserver.jar' | awk '{print $1}' | xargs kill -9
sleep 1
echo 'ios tcpserver has stopped!'

echo 'stop android tcpserver...'
java -jar yx-android-tcpserver.jar stop
sleep 3
jps -mlvV | grep 'yx-android-tcpserver.jar' | awk '{print $1}' | xargs kill -9
sleep 1
echo 'android tcpserver has stopped!'