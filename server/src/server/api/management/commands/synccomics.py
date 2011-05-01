from HTMLParser import HTMLParser
from urllib2 import urlopen
import re
import traceback

from django.db.models import Max
from django.core.management.base import BaseCommand

from server.api.models import Comic

class XkcdParser(HTMLParser):
    _perm_link = re.compile(r'http://xkcd.com/(\d+)/').search
    _img_link = re.compile(r'http://imgs.xkcd.com/comics/(.*)\.(jpg|png)').match

    def __init__(self, *args, **kwargs):
        HTMLParser.__init__(self, *args, **kwargs)
        self._in_h3 = False

    def handle_starttag(self, tag, attr):
        if tag == 'img':
            title = img_name = img_type = message = None
            for attr_name, attr_value in attr:
                if attr_name == 'alt' and attr_value:
                    title = attr_value
                elif attr_name == 'src':
                    match = self._img_link(attr_value)
                    if match:
                        img_name = match.group(1)
                        if match.group(2) == 'jpg':
                            img_type = Comic.IMG_TYPE_JPEG
                        else:
                            img_type = Comic.IMG_TYPE_PNG
                elif attr_name == 'title' and attr_value:
                    message = attr_value
            if title and img_name and img_type is not None and message:
                self.title = title
                self.img_name = img_name
                self.img_type = img_type
                self.message = message
        elif tag == 'h3':
            self._in_h3 = True

    def handle_data(self, data):
        if self._in_h3 and data:
            match = self._perm_link(data)
            if match:
                self.number = int(match.group(1))

    def handle_endtag(self, tag):
        if tag == 'h3':
            self._in_h3 = False

    def reset(self):
        HTMLParser.reset(self)
        self._in_h3 = False
        self.number = None
        self.title = None
        self.img_name = None
        self.img_type = None
        self.message = None


class Command(BaseCommand):
    help = 'Sync the comics database with xkcd.com'

    def __init__(self, *args, **kwargs):
        BaseCommand.__init__(self, *args, **kwargs)
        self.parser = XkcdParser()

    def handle(self, *args, **options):
        self.stdout.write('Starting sync.\n')

        try:
            sync_to = self.sync_comic().number
        except:
            self.stderr.write('Failed to get latest comic info!\n')
            traceback.print_exc(file=self.stderr)
            sync_to = Comic.objects.all().aggregate(
                Max('number'))['number__max']

        for number in range(1, sync_to):
            self.sync_comic(number)

        self.stdout.write('Complete.\n')

    def get_comic_info(self, number=None):
        if number is None:
            uri = 'http://xkcd.com/'
        else:
            uri = 'http://xkcd.com/%d/' % number
        self.parser.reset()
        self.parser.feed(urlopen(uri).read().decode('utf-8'))
        return dict((key, getattr(self.parser, key)) for key in
                    ('number', 'title', 'img_name', 'img_type', 'message'))

    def sync_comic(self, number=None):
        if number is None:
            comic_info = self.get_comic_info()
            number = comic_info['number']
        else:
            comic_info = None

        try:
            comic = Comic.objects.get(pk=number)
        except Comic.DoesNotExist:
            # New comic
            comic = Comic(number=number)
        else:
            if comic.sync_state in (Comic.SYNC_STATE_OK,
                                    Comic.SYNC_STATE_ERROR):
                # Already synced.
                return comic

        set_sync_error = False

        if comic_info is None:
            try:
                comic_info = self.get_comic_info(number=comic.number)
            except:
                self.stderr.write('Failed to get comic %r info!\n' % number)
                traceback.print_exc(file=self.stderr)
                set_sync_error = True

        self.stdout.write('Syncing comic %d...' % number)

        try:
            assert comic_info['number'] == comic.number
            comic.title = comic_info['title']
            comic.img_name = comic_info['img_name']
            comic.img_type = comic_info['img_type']
            comic.message = comic_info['message']
            comic.sync_state = Comic.SYNC_STATE_OK
            comic.full_clean()
            comic.save()
        except:
            set_sync_error = True

        if set_sync_error:
            comic.set_error_sync_state()
            comic.full_clean()
            comic.save()
            self.stdout.write('failed!\n')
        else:
            self.stdout.write('done.\n')

        return comic
