from piston.emitters import Emitter

from server.api.comics_pb2 import ComicList
from server.api.models import Comic

class ComicProtobufEmitter(Emitter):
    def render(self, request):
        comic_messages = []
        for comic_dict in self.construct():
            comic = ComicList.Comic()
            comic.number = comic_dict['number']
            comic.title = comic_dict['title']
            comic.img_name = comic_dict['img_name']
            if comic_dict['img_type'] == Comic.IMG_TYPE_JPEG:
                comic.img_type = Comic.IMG_TYPE_JPEG
            comic.message = comic_dict['message']
            comic_messages.append(comic)
        comics = ComicList()
        comics.comics.extend(comic_messages)
        return comics.SerializeToString()


_registered = False
def register():
    if not _registered:
        global _registered
        Emitter.register('comic-protobuf', ComicProtobufEmitter,
            'application/x-protobuf')
        _registered = True
