import argparse
import logging

import spacy

from datetime import datetime
import json
import re
import os

SUPPORTED_DATE_FORMATS = ['%a, %d %b %Y', # e.g., 'Tue, 20 Oct 2020'
                          '%a, %b %d, %Y', # e.g., 'Tue, Oct 20, 2020'
                          '%d %b %Y', # e.g., '20 Oct 2020'
                          '%a, %d %b %Y %H:%M:%S %z', # e.g., 'Tue, 20 Oct 2020 13:44:57 +0000'
                          '%Y-%m-%d', # e.g., '2020-10-23'
                          '%m/%d/%Y', # e.g., '08/21/2024'
                          '%m/%d/%y', # e.g., '10/19/99'
                          '%m/%d/%Y at %I:%M %p', # e.g., '08/21/2024 at 1:12 p.m.'
                          '%b %d, %Y, at %I:%M %p', # e.g., 'Apr 19, 2023, at 3:46 PM',
                          '%b %d, %Y', # e.g., 'Apr 19, 2023',
                          '%b %d %Y', # e.g., 'Apr 19 2023',
                          '%Y/%m/%d %H:%M:%S', # e.g., '2020-05-01 00:00:00',
                          '%A, %B %d, %Y %I:%M %p', # e.g., 'Monday, October 19, 2020 3:14 PM',
                          '%A, %B %d, %Y', # e.g., 'Monday, October 19, 2020',
                          '%A %B %d, %Y', # e.g., 'Monday October 19, 2020',
                          '%B %d, %Y', # e.g., 'October 15, 2019'
                          '%B %d %Y', # e.g., 'October 15 2019'
                          '%B %Y', # e.g., 'October 2019'
                          '%b %Y', # e.g., 'Oct 2019'
                          '%Y'] # e.g., '2023'

def parse_date(text, doc_name, page_num):
    if not text or text.isalpha():
        return None
    only_words = True
    for part in text.split():
        if not part.isalpha():
            only_words = False
            break
    if only_words:
        return None

    for fmt in SUPPORTED_DATE_FORMATS:
        try:
            return [datetime.strptime(text, fmt).strftime('%Y-%m-%d')]
        except ValueError:
            pass
    dates = re.findall(r'(\d{1,2})/(\d{1,2})/(\d{2,4})', text)
    if dates:
        iso_dates = []
        for res in dates:
            try:
                if len(res[2]) == 2:
                    iso_dates.append(datetime.strptime('/'.join(res), '%m/%d/%y').strftime('%Y-%m-%d'))
                else:
                    iso_dates.append(datetime.strptime('/'.join(res), '%m/%d/%Y').strftime('%Y-%m-%d'))
            except ValueError:
                log_bad_date_format('/'.join(res), doc_name, page_num)
        return iso_dates

    log_bad_date_format(text, doc_name, page_num)
    return None


def log_bad_date_format(txt: str, doc_name, page_num):
    logging.debug('Unknown date format in document ' + doc_name + ' page ' + page_num + ': ' + txt)



def pages_to_records(in_path, out_path, nlp):
    for root, dirs, files in os.walk(in_path):
        for dirname in dirs:
            out_dir = os.path.join(out_path, dirname)
            os.makedirs(out_dir)
            pages_to_records(os.path.join(root, dirname), out_dir, nlp)
        for filename in files:
            m = re.fullmatch(r'([^.]+)\.pdf_page(\d+)\.pdf\.txt', filename)
            if m:
                doc_name = m.group(1)
                page_num = m.group(2)
                file_path = os.path.join(root, filename)
                record = page_to_record(file_path, doc_name, page_num, nlp)
                with open(os.path.join(out_path, filename + ".record"),"w") as f:
                    f.write(json.dumps(record))


def page_to_record(fullpath, doc_name, page_num, nlp):
    with open(fullpath,"r") as f:
        content = f.read()

    doc = nlp(content)
    entities = doc.ents

    ppl = []
    dates = []
    places = []
    orgs = []
    groups = []

    for e in entities:
        label = e.label_
        if label == "GPE" or label == "LOC":
            places.append(e.text)
        elif label == "PERSON":
            ppl.append(e.text)
        elif label == "DATE":
            iso_date = parse_date(e.text.strip('\r\n \t,').replace(os.linesep, ''), doc_name, page_num)
            if iso_date:
                dates.extend(iso_date)
        elif label == "ORG":
            orgs.append(e.text)
        elif label == "NORP":
            groups.append(e.text)

    record = { "origFile": doc_name,
               "page": page_num,
               "people": list(set(ppl)),
               "dates": list(set(dates)),
               "places": list(set(places)),
               "orgs": list(set(orgs)),
               "groups": list(set(groups)),
               "txt": content.replace('"', '\'').replace('\n', '    ')}

    return record


def main(input_path, output_path):
    if not os.path.exists(output_path):
        os.makedirs(output_path)

    nlp = spacy.load("en_core_web_trf", disable=["tagger", "parser", "attribute_ruler", "lemmatizer"])
    pages_to_records(input_path, output_path, nlp)


if __name__=="__main__":
    print(datetime.now())

    parser = argparse.ArgumentParser(
        prog='PagesToRecords',
        description='Converts text files into searchable ElasticSearch records',
        epilog='Use the Java program to upload the resulting records to ES')
    parser.add_argument('input_path')
    parser.add_argument('output_path')

    args = parser.parse_args()
    print(args.input_path, args.output_path)
    main(args.input_path, args.output_path)
    print(datetime.now())
