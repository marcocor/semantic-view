from lxml import etree
from html2text import html2text
import dateparser
import unicodecsv as csv
import os
import logging
import argparse
import tagme
import re
from scipy.weave.converters import default

MAIL_REGEX = r"\w+@[a-zA-Z_]+?\.[a-zA-Z]{2,3}"
URL_REGEX = r"(https?|ftp)://[^\s/$.?#].[^\s]*"

def clean_text(text):
    return re.sub("({})|({})".format(MAIL_REGEX, URL_REGEX), " ", text)

def item_to_data(item):
    key = item.findtext("key")
    title = item.findtext("title")
    body = clean_text(html2text(item.findtext("description")))
    time_str = item.xpath('./customfields/customfield[customfieldname = "Data invio mail"]/customfieldvalues/customfieldvalue/text()')[0]
    time = dateparser.parse(time_str)
    if not time:
        time = dateparser.parse(time_str[4:])
    if not time:
        return None
        logging.warning("Could not parse date {} in document {}".format(time_str, key))

    return (key, title, body, time.isoformat())
    
def get_documents(xml_file):
    tree = etree.parse(xml_file)
    if tree.getroot() is None:
        return None

    items = tree.xpath('//item[.//customfieldname = "Data invio mail"]')
    return filter(lambda item: item is not None, map(item_to_data, items))

DOCS_CSV_FIELDS = ['key', 'title', 'body', 'time']
ENTITIES_CSV_FIELDS = ['key', 'entity', 'score', 'time']
DOCS_SUBFOLDER = 'docs'
ENTITIES_SUBFOLDER = 'entities'

if __name__ == "__main__":
    logging.basicConfig(level=logging.DEBUG)
    
    parser = argparse.ArgumentParser()
    parser.add_argument('--infiles', dest='infiles', nargs='+', help='Jira XML input files.')
    parser.add_argument('--outdir', dest='outdir', help='Output directory of CSV files containing annotations.')
    parser.add_argument('--gcube-token', dest='gcube_token', help='Gcube authentication token to access Tagme API.')
    parser.add_argument('--lang', dest='lang', default='en', help='Language of the documents. Must be accepted by Tagme.')
    args = parser.parse_args()

    docs_path_base = os.path.join(args.outdir, DOCS_SUBFOLDER)
    entities_path_base = os.path.join(args.outdir, ENTITIES_SUBFOLDER)
    if not os.path.isdir(docs_path_base):
        os.makedirs(docs_path_base)
    if not os.path.isdir(entities_path_base):
        os.makedirs(entities_path_base)
    
    for xml_file in args.infiles:
        logging.info("Processing {}".format(xml_file))
        for i, doc in enumerate(get_documents(xml_file)):
            if doc is None:
                logging.warning("Could not parse document {} from {}".format(i, xml_file))
                continue
            key, title, body, time = doc
            
            doc_path = "{}.csv".format(os.path.join(docs_path_base, key))
            entities_path = "{}.csv".format(os.path.join(entities_path_base, key))
            
            if (os.path.isfile(doc_path) and os.path.isfile(entities_path)):
                logging.info("Document {} already annotated, skipping.".format(key))
                continue
            
            logging.info("Annotating document key={} length={} ({})".format(key, len(body), xml_file))
            tagme_response = tagme.annotate(u'{} {}'.format(title, body), args.gcube_token, lang=args.lang)
            if not tagme_response:
                logging.warning("Could not annoate document {} from {} (key {})".format(i, xml_file, key))
                continue
            annotations = tagme_response.get_annotations(min_rho=0.2)
            logging.info("Found {} annotations".format(len(annotations)))
            
            with open(doc_path, 'wb') as csv_doc_out:
                w = csv.DictWriter(csv_doc_out, encoding='utf-8', fieldnames=DOCS_CSV_FIELDS)
                w.writerow({'key': key, 'title': title, 'body': body, 'time': time})

            with open(entities_path, 'wb') as csv_entities_out:
                w = csv.DictWriter(csv_entities_out, encoding='utf-8', fieldnames=ENTITIES_CSV_FIELDS)
                for annotation in annotations:
                    w.writerow({'key': key, 'entity': annotation.entity_title, 'score': annotation.score, 'time': time})
            
