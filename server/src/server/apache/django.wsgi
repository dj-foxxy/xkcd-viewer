import os
import sys

def abspath(relative_path):
    return os.path.abspath(os.path.join(os.path.dirname(__file__), relative_path))

sys.path.append(abspath('../..'))
sys.path.append(abspath('..'))
os.environ['DJANGO_SETTINGS_MODULE'] = 'server.settings'

import django.core.handlers.wsgi

application = django.core.handlers.wsgi.WSGIHandler()
