from functools import wraps
import os.path

def abspath(origin_path, relative_path):
    return os.path.abspath(os.path.join(os.path.dirname(origin_path),
        relative_path))

def fixed_origin_abspath(origin_path):
    @wraps(abspath)
    def wrapper(relative_path):
        return abspath(origin_path, relative_path)
    return wrapper
