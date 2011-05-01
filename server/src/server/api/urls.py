from django.conf.urls.defaults import patterns
from piston.resource import Resource

from server.api import emitters
from server.api.handlers import ComicHandler

emitters.register()

urlpatterns = patterns('',
    (r'^comics/$', Resource(ComicHandler), {'emitter_format': 'comic-protobuf'}),
)
