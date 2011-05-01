from piston.handler import BaseHandler

from server.api.models import Comic
from piston.utils import rc

class ComicHandler(BaseHandler):
    allowed_methods = ('GET')
    model = Comic

    def read(self, request):
        filter = {'sync_state': Comic.SYNC_STATE_OK}
        if 'after' in request.GET:
            try:
                number = int(request.GET['after'])
            except ValueError:
                return rc.BAD_REQUEST
            filter['number__gt'] = number
        return Comic.objects.filter(**filter)
