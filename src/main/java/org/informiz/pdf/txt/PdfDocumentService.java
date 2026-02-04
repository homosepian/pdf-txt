package org.informiz.pdf.txt;


import org.openpdf.text.pdf.PdfBatchUtils;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.io.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.exit;

/**
 * A class containing methods for processing PDF files.
 */
public class PdfDocumentService {

    public static final Pattern PAGE_NAME_PATTERN = Pattern.compile("[^.]+\\.pdf_page(\\d+)\\.pdf");

    static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.out.println("Please provide full path to a source-folder with PDF files");
            System.out.println("Example usage:");
            System.out.println("java -cp path/to/app.jar org.informiz.pdf.txt.PdfDocumentService /full/path/to/pdf/folder/");
            exit(1);
        }

        File srcFolder = new File(args[0]);
        if (! srcFolder.exists()) {
            System.out.println("Source-folder " + args[0] + " does not exist");
            exit(2);
        }

        System.out.println("Processing documents under " + args[0] + " and sub-folders...");

        try {
            Utils.processFilesInFolder(srcFolder, srcFolder.getName(), Utils::concatPages, null);
            System.out.println("Done extracting text, output available at " + System.getProperty("java.io.tmpdir"));
            System.out.println("You can find the extracted text-pages under " +
                    System.getProperty("java.io.tmpdir") +
                    " inside folders named 'converted_' followed by original PDF file name and random numbers");

        } catch (IOException e) {
            throw new RuntimeException("Unexpected error while processing files", e);
        }
    }


    /**
     * Split a PDF file into individual PDF pages in the output folder.
     * @param file the file to split
     * @param outputFolder where to save the individual pages. File names follow the pattern:
     *                     originalFileName.pdf_page123.pdf
     */
    public static void splitPdf(File file, File outputFolder) {
        splitPdf(file, outputFolder, file.getName());
    }

    /**
     * Split a PDF file into individual PDF pages in the output folder.
     * @param file the file to split
     * @param outputFolder where to save the individual pages. File names follow the pattern:
     *                     filenamePrefix.pdf_page123.pdf
     * @param filenamePrefix the prefix for the resulting file names - should indicate the original filename
     *                       (possibly including directory/path of the original file)
     */
    public static void splitPdf(File file, File outputFolder, String filenamePrefix) {
        PdfBatchUtils.SplitJob splitJob = new PdfBatchUtils.SplitJob(file.toPath(), outputFolder.toPath(), filenamePrefix);
        PdfBatchUtils.batchSplit(List.of(splitJob), paths -> {}, throwable -> {});
    }

    /**
     * Extract text from a single-page PDF document and writes it into the output folder.
     * @param pdfFile the file to extract text from. File-name is expected to end with e.g., '.pdf_page123.pdf'.
     * @param outputFolder where to save the resulting text file. File name follows the pattern:
     *                            originalFileName.pdf_page123.pdf.txt
     */
    public static void extractText(File pdfFile, File outputFolder) {
        Matcher namePattern = PAGE_NAME_PATTERN.matcher(pdfFile.getName());
        if (! namePattern.find())
            return;

        int pageNum;
        try {
            pageNum = Integer.parseInt(namePattern.group(1));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Unexpected state: can't extract page number from file-name " +
                    pdfFile.getName(), e);
        }

        String content = getContent(pdfFile, pageNum);

        String filename = pdfFile.getName() + ".txt";

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputFolder, filename)))) {
            try {
                writer.write(content);
                writer.newLine();
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException("Unexpected state: failed to write page " + pageNum, e);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unexpected state: cannot write file for page " + pageNum, e);
        }
    }

    private static String getContent(File pdfFile, int pageNum) {
        String content = "No text found in page " + pageNum;
        try (PdfReader reader = new PdfReader(new FileInputStream(pdfFile))) {
            if (reader.getNumberOfPages() > 0) {
                PdfTextExtractor extractor = new PdfTextExtractor(reader);
                try {
                    content = extractor.getTextFromPage(1);
                } catch (Exception e) {
                    content = "Empty content found in page " + pageNum;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Unexpected state: failed to process page " + pageNum, e);
        }
        return content;
    }
}