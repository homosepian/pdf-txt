# NLP Pipeline
If you have [Python 3.10+ installed](https://www.python.org/downloads/), you can use 
[spaCy](https://spacy.io/usage) to process the text and extract Named-Entities, including dates.
Note that on an average laptop it can take ~1min to process ~150 pages, which means a whole day for ~200,000 pages, 
for example. 

However - you only need to run the pipeline once in order to produce the ElasticSearch records. 
After that you can just upload the already-processed records to ElasticSearch in seconds/minutes whenever you want to 
search them. Re-processing is required only if/when there's a new version of the pipeline.

## Run locally
The script takes as parameters the full path to a folder containing text-pages, and an output folder for the processed 
records:
`python process.py /full/path/to/text_pages /full/path/to/output/folder`

