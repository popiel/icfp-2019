#!/bin/bash

best=$(sed 's/[^A-Z]//g' solutions/prob-${1}.sol | wc -c)
sbt "run  problems/prob-${1}.desc"
cost=$(sed 's/[^A-Z]//g' problems/prob-${1}.sol | wc -c)
if [ $best -eq 0 ] || [ $cost -lt $best ]; then
  echo Improving from $best to $cost
  cp problems/prob-${1}.sol solutions/
else
  echo Prior $best remains better than $cost
fi
