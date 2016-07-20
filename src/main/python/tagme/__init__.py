__version__ = '0.1'

__all__ = [
    'annotate', 'Annotation', 'Response',
    ]

__author__ = 'Marco Cornolti <cornolti@di.unipi.it>'

import requests
import simplejson
import logging

DEFAULT_API = "https://tagme.d4science.org/tagme/tag"
DEFAULT_LANG = "en"
WIKIPEDIA_URI_BASE = "https://{}.wikipedia.org/wiki/{}"

class Annotation():
    def __init__(self, ann_json):
        self.begin = int(ann_json.get("start"))
        self.end = int(ann_json.get("end"))
        self.entity_id = int(ann_json.get("id"))
        self.entity_title = ann_json.get("title")
        self.score = float(ann_json.get("rho"))
        self.mention = ann_json.get("spot")
        
    def __str__(self):
        return "{} -> {} (score: {})".format(self.mention, self.entity_title, self.score)
    
    def uri(self, lang=DEFAULT_LANG):
        return title_to_uri(self.entity_title, lang)

class Response():
    def __init__(self, json_content):
        self.annotations = [Annotation(ann_json) for ann_json in json_content["annotations"]]
        self.time = int(json_content["time"])
        
    def get_annotations(self, min_rho=None):
        return filter(lambda d: min_rho is None or d.score > min_rho, self.annotations)
        
    def __str__(self):
        return "{}msec, {} annotations".format(self.time, len(self.annotations))

def annotate(text, gcube_token, lang=DEFAULT_LANG, api=DEFAULT_API):
        payload = {"text": text.encode("utf-8"), "gcube-token":gcube_token, "long_text": 3}
        r = requests.post(api, payload)
        logging.debug("Calling {}".format(r.url))
        if r.status_code != 200:
            logging.warning("Tagme returned status code {} message:\n{}".format(r.status_code, r.content))
            return None
        return Response(simplejson.loads(r.content))

def title_to_uri(entity_title, lang=DEFAULT_LANG):
    return WIKIPEDIA_URI_BASE.format(lang, entity_title.replace(" ", "_"))