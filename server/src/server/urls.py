from django.conf.urls.defaults import patterns, include

import server.api.urls

urlpatterns = patterns('',
    (r'', include(server.api.urls)),
)
