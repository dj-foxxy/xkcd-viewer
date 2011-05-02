#!/bin/bash
set -o errexit
set -o nounset

setup_Dir="`dirname $0 | xargs readlink -e`"

setup_Log="$setup_Dir/src/server/api/synccomics.log"
setup_Job="python2.6 $setup_Dir/src/server/manage.py synccomics >> $setup_Log 2>&1"
setup_Path='/tmp/jobs.cron'

echo "

PYTHONPATH=\"\$PYTHONPATH:$setup_Dir/src\"

0 5 * * MON,WED,FRI $setup_Job

" > "$setup_Path"

crontab "$setup_Path"

setup_ServerName='192.168.1.80'
setup_DocumentRoot="$setup_Dir/src/server"
setup_WsgiPath="$setup_DocumentRoot/apache/django.wsgi"

echo "
NameVirtualHost *:8000

<VirtualHost *:8000>

    ServerName $setup_ServerName

    WSGIScriptAlias / $setup_WsgiPath
    WSGIPassAuthorization On

    DocumentRoot $setup_DocumentRoot

    ErrorLog \${APACHE_LOG_DIR}/xkcd-server.log

</VirtualHost>
" | sudo tee /etc/apache2/sites-available/xkcd-server

if ! grep -e 'Listen 8000' /etc/apache2/ports.conf; then
	echo -e "\nListen 8000\n" | sudo tee -a /etc/apache2/ports.conf
fi

sudo a2ensite xkcd-server
sudo service apache2 restart

