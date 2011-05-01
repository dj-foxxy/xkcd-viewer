all:
	protoc -I=. --java_out=. --python_out=. comics.proto
	mv com/appspot/mancocktail/xkcdviewer/ComicProtos.java ../viewer/src/com/appspot/mancocktail/xkcdviewer/
	rm -r com
	mv comics_pb2.py  ../server/src/server/api/
