# PDF to Searchable Text

This repository contains code for processing PDF files and making the text searchable.

## Work In Progress
Currently, you can split a PDF file into individual pages and extract the text from these pages. 
A [test class](src/test/java/org/informiz/pdf/txt/PdfDocumentServiceTest.java) is available for running it locally.
The resulting files are written to the default "tmp" directory of your system (e.g., /tmp in linux).

## The Epstein text
As an example, you can find the text extracted from the first release of the Epstein files in the 
[test/resources/converted](src/test/resources/converted) folder. 
Note that only one of the original PDF files are available in this repository, since they are quite big and are 
available elsewhere online.
