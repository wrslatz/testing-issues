#!/bin/bash


loc=`dirname $0`
cd $loc


# use jdk8 as the default
JAVA_HOME=/path/to/jdk8
JRE=${JAVA_HOME}/bin/java


# everything is relative to this script
echo "Launching Vertx Test..."
exec ${JRE} -cp "./*:./config/*" -DHTTP_PORT=54321 -jar VertxTest-fat.jar
