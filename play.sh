#!/bin/sh
java -Xms100m -Xmx256m -XX:PerfDataSamplingInterval=10000 -jar build/libs/*.jar
