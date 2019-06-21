#!/bin/sh

cd `dirname $0`
[ -r solutions.zip ] || touch solutions.zip --date="10 days ago"
find solutions -name '*.sol' -newer solutions.zip -print -quit | grep -qs '.' && ( rm -f solutions.zip 2>/dev/null; zip -j -r solutions solutions -i '*.sol' )
[ -r next-submission ] || touch next-submission --date="10 days ago"
[ solutions.zip -nt next-submission ] && curl -F "private_id=$(tail -1 token)" -F "file=@solutions.zip" https://monadic-lab.org/submit && touch next-submission --date="+10 minutes"
