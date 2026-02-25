# NLP Pipeline
If you have [Python 3.10+ installed](https://www.python.org/downloads/), you can use 
[spaCy](https://spacy.io/usage) to process the text and extract Named-Entities, including dates.
Note that on an average laptop it can take ~1min to process ~150 pages, which means a whole day for ~200,000 pages, 
for example. You can make the execution several times faster by running it on a local installation of 
[pyspark](https://spark.apache.org/docs/latest/api/python/getting_started/install.html). If you run it in the cloud 
then a small cluster with a few dozens of CPUs should finish the processing within minutes.

However - you only need to run the pipeline once in order to produce the ElasticSearch records. 
After that you can just upload the already-processed records to ElasticSearch in seconds/minutes whenever you want to 
search them. Re-processing is required only if/when there's a new version of the pipeline.

## Run locally
The script takes as parameters the full path to a folder containing text-pages, and an output folder for the processed 
records. You will need to first run the PdfDocumentService in order to extract the text from your PDF documents and 
make them available to the pipeline:

`java -cp path/to/app.jar org.informiz.pdf.txt.PdfDocumentService /full/path/to/pdf/folder/"`

The text files will be created under a folder called `text_pages` in your user-directory. The full path to this folder 
will be printed once the program finishes converting all the documents in the folder `/full/path/to/pdf/folder/`.

Give the pipeline the full path to the created `text_pages` folder and a full path to an output folder, where the 
records will be created:

`python process.py /full/path/to/text_pages /full/path/to/records/folder`

You can then upload the records to ElasticSearch any time you want to search them using:

`ES_LOCAL_API_KEY=je0i4rfvnho....ero8p9vk4jw== java -cp path/to/app.jar org.informiz.pdf.txt.ElasticSearchService /full/path/to/records/folder/ --records`



