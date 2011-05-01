all:
	protoc -I=. --java_out=. --python_out=. comics.proto
	cp com/appspot/mancocktail/xkcdviewer/ComicProtos.java ../xkcd-viewer/src/com/appspot/mancocktail/xkcdviewer/
	cp comics_pb2.py  ../xkcd-server/src/server/api/

