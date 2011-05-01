#!/bin/bash
set -o errexit
set -o nounset

setup_Dir="`dirname $0 | xargs readlink -e`"
setup_Log="$setup_Dir/src/server/api/synccomics.log"
setup_Job="python2.7 $setup_Dir/src/server/manage.py synccomics >> $setup_Log 2>&1"
setup_Path='/tmp/jobs.cron'

echo "

PYTHONPATH=\"$PYTHONPATH:$setup_Dir/src\"

0 5 * * MON,WED,FRI $setup_Job

" > "$setup_Path"

crontab "$setup_Path"
