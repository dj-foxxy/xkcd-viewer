# Generated by the protocol buffer compiler.  DO NOT EDIT!

from google.protobuf import descriptor
from google.protobuf import message
from google.protobuf import reflection
from google.protobuf import descriptor_pb2
# @@protoc_insertion_point(imports)



DESCRIPTOR = descriptor.FileDescriptor(
  name='comics.proto',
  package='xkcdviewer',
  serialized_pb='\n\x0c\x63omics.proto\x12\nxkcdviewer\"\xdd\x01\n\tComicList\x12+\n\x06\x63omics\x18\x01 \x03(\x0b\x32\x1b.xkcdviewer.ComicList.Comic\x1a\xa2\x01\n\x05\x43omic\x12\x0e\n\x06number\x18\x01 \x02(\r\x12\r\n\x05title\x18\x02 \x02(\t\x12\x10\n\x08img_name\x18\x03 \x02(\t\x12:\n\x08img_type\x18\x04 \x01(\x0e\x32#.xkcdviewer.ComicList.Comic.ImgType:\x03PNG\x12\x0f\n\x07message\x18\x05 \x02(\t\"\x1b\n\x07ImgType\x12\x07\n\x03JPG\x10\x00\x12\x07\n\x03PNG\x10\x01\x42\x31\n\"com.appspot.mancocktail.xkcdviewerB\x0b\x43omicProtos')



_COMICLIST_COMIC_IMGTYPE = descriptor.EnumDescriptor(
  name='ImgType',
  full_name='xkcdviewer.ComicList.Comic.ImgType',
  filename=None,
  file=DESCRIPTOR,
  values=[
    descriptor.EnumValueDescriptor(
      name='JPG', index=0, number=0,
      options=None,
      type=None),
    descriptor.EnumValueDescriptor(
      name='PNG', index=1, number=1,
      options=None,
      type=None),
  ],
  containing_type=None,
  options=None,
  serialized_start=223,
  serialized_end=250,
)


_COMICLIST_COMIC = descriptor.Descriptor(
  name='Comic',
  full_name='xkcdviewer.ComicList.Comic',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    descriptor.FieldDescriptor(
      name='number', full_name='xkcdviewer.ComicList.Comic.number', index=0,
      number=1, type=13, cpp_type=3, label=2,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    descriptor.FieldDescriptor(
      name='title', full_name='xkcdviewer.ComicList.Comic.title', index=1,
      number=2, type=9, cpp_type=9, label=2,
      has_default_value=False, default_value=unicode("", "utf-8"),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    descriptor.FieldDescriptor(
      name='img_name', full_name='xkcdviewer.ComicList.Comic.img_name', index=2,
      number=3, type=9, cpp_type=9, label=2,
      has_default_value=False, default_value=unicode("", "utf-8"),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    descriptor.FieldDescriptor(
      name='img_type', full_name='xkcdviewer.ComicList.Comic.img_type', index=3,
      number=4, type=14, cpp_type=8, label=1,
      has_default_value=True, default_value=1,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    descriptor.FieldDescriptor(
      name='message', full_name='xkcdviewer.ComicList.Comic.message', index=4,
      number=5, type=9, cpp_type=9, label=2,
      has_default_value=False, default_value=unicode("", "utf-8"),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
    _COMICLIST_COMIC_IMGTYPE,
  ],
  options=None,
  is_extendable=False,
  extension_ranges=[],
  serialized_start=88,
  serialized_end=250,
)

_COMICLIST = descriptor.Descriptor(
  name='ComicList',
  full_name='xkcdviewer.ComicList',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    descriptor.FieldDescriptor(
      name='comics', full_name='xkcdviewer.ComicList.comics', index=0,
      number=1, type=11, cpp_type=10, label=3,
      has_default_value=False, default_value=[],
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
  ],
  extensions=[
  ],
  nested_types=[_COMICLIST_COMIC, ],
  enum_types=[
  ],
  options=None,
  is_extendable=False,
  extension_ranges=[],
  serialized_start=29,
  serialized_end=250,
)

_COMICLIST_COMIC.fields_by_name['img_type'].enum_type = _COMICLIST_COMIC_IMGTYPE
_COMICLIST_COMIC.containing_type = _COMICLIST;
_COMICLIST_COMIC_IMGTYPE.containing_type = _COMICLIST_COMIC;
_COMICLIST.fields_by_name['comics'].message_type = _COMICLIST_COMIC
DESCRIPTOR.message_types_by_name['ComicList'] = _COMICLIST

class ComicList(message.Message):
  __metaclass__ = reflection.GeneratedProtocolMessageType
  
  class Comic(message.Message):
    __metaclass__ = reflection.GeneratedProtocolMessageType
    DESCRIPTOR = _COMICLIST_COMIC
    
    # @@protoc_insertion_point(class_scope:xkcdviewer.ComicList.Comic)
  DESCRIPTOR = _COMICLIST
  
  # @@protoc_insertion_point(class_scope:xkcdviewer.ComicList)

# @@protoc_insertion_point(module_scope)
