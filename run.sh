#!/bin/bash

problems=$(find problems -name prob-${1}.desc -print | sort)
sbt "run $problems"
exit 0
for p in $problems; do
  sol=$(echo $p | sed 's/problems.//;s/\.desc/.sol/')
  best=$(sed 's/[^A-Z]//g' solutions/$sol | wc -c)
  cost=$(sed 's/[^A-Z]//g' problems/$sol | wc -c)
  if [ $best -eq 0 ] || [ $cost -lt $best ]; then
    echo $sol: Improving from $best to $cost
    cp problems/$sol solutions/
  else
    echo $sol: Prior $best remains better than $cost
  fi
done
