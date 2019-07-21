#!/bin/bash
script_dir=$(cd $(dirname "$0") && pwd)
java -Xms100m -Xmx256m -XX:PerfDataSamplingInterval=10000 -jar "${script_dir}/build/libs/mp3transform-fat.jar"
