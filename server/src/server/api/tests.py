from django.test import Client, TestCase

from server.api.comics_pb2 import ComicList

class ComicHandlerTest(TestCase):
    def setUp(self):
        self.client = Client()
        self.comics = ComicList()

    def test_get_all_comics(self):
        self.comics.ParseFromString(
            self.client.get('/comics/').content)
        self.assertEqual(len(self.comics.comics), 890)

    def test_get_some_comics(self):
        self.comics.ParseFromString(
            self.client.get('/comics/?after=800').content)
        self.assertEqual(len(self.comics.comics), 91)
