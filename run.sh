#!/bin/bash

best=$(sed 's/[^A-Z]//g' solutions/prob-${1}.sol | wc -c)
sbt "run  problems/prob-${1}.desc"
cost=$(sed 's/[^A-Z]//g' problems/prob-${1}.sol | wc -c)
([ $best -eq 0 ] || [ $cost -lt $best ]) && cp problems/prob-${1}.sol solutions/
