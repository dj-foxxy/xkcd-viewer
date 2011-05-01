from django.core.exceptions import ValidationError
from django.db.models import IntegerField, Model, TextField

class Comic(Model):
    IMG_TYPE_JPEG = 0
    IMG_TYPE_PNG = 1

    IMG_TYPE_CHOICES = (
        (IMG_TYPE_JPEG, 'JPEG'),
        (IMG_TYPE_PNG, 'PNG')
    )

    SYNC_STATE_OK = 0
    SYNC_STATE_SYNCING = 1
    SYNC_STATE_ERROR = 2

    SYNC_STATE_CHOICES = (
        (SYNC_STATE_OK, 'OK'),
        (SYNC_STATE_SYNCING, 'Syncing'),
        (SYNC_STATE_ERROR, 'Error')
    )

    number = IntegerField(primary_key=True)
    title = TextField(blank=True, null=True)
    img_name = TextField(blank=True, null=True)
    img_type = IntegerField(blank=True, choices=IMG_TYPE_CHOICES, null=True)
    message = TextField(blank=True, null=True)
    sync_state = IntegerField(choices=SYNC_STATE_CHOICES,
        default=SYNC_STATE_SYNCING)

    def set_error_sync_state(self):
        self.title = self.img_name = self.img_type = self.message = None
        self.sync_state = self.SYNC_STATE_ERROR

    def clean(self):
        if self.number <= 0:
            raise ValidationError(u'number must be greater than 0')
        required_group = (self.title, self.img_name, self.img_type,
                          self.message)
        if len(filter(lambda i: i is not None, required_group)) \
        not in (0, len(required_group)):
            raise ValidationError(
                u'title, img_name, img_type and message must either all be '
                u'present or none must be present.')
