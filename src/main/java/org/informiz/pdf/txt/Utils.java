package org.informiz.pdf.txt;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;

import static org.informiz.pdf.txt.PdfDocumentService.getNumPages;

public class Utils {
    // Split a PDF file into pages and extract the text from each page.
    // Intermediate files/folders are created in the default temp folder.
    // Returns the folder containing the text pages.
    protected static File pdfToTxt(File pdfFile, String filenamePrefix, File outputFolder) throws IOException {
        File pagesFolder = Files.createTempDirectory("split_" + filenamePrefix).toFile();
        boolean splitRequired = isSplitRequired(pdfFile, filenamePrefix, pagesFolder);

        if (splitRequired) {
            PdfDocumentService.splitPdf(pdfFile, pagesFolder, filenamePrefix);

            // TODO: need sub-folders for text-pages?
/*
            File txtFolder = new File(outputFolder, "converted_" + filenamePrefix);
            if (! (txtFolder.mkdirs() || txtFolder.isDirectory()))
                throw new IllegalStateException("Failed to create output folder " + txtFolder.getCanonicalPath());
*/
        }

        try (Stream<Path> pdfPages = Files.list(pagesFolder.toPath())) {
            pdfPages.forEach(path -> PdfDocumentService.extractText(path.toFile(), outputFolder));
        }
        return outputFolder;
    }

    private static boolean isSplitRequired(File pdfFile, String filenamePrefix, File outputFolder) {
        boolean splitRequired = true;
        int numPages = getNumPages(pdfFile);
        if (numPages == 0) {
            splitRequired = false;
        }
        if (numPages == 1) {
            Path targetPath = Paths.get(outputFolder.getAbsolutePath(), filenamePrefix + "_page1.pdf");
            Path originalPath = pdfFile.toPath();
            try {
                Files.copy(originalPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            splitRequired = false;
        }
        return splitRequired;
    }

    public static File createTxtPagesFolder() {
        File outputFolder = new File("text_pages");
        if (! outputFolder.exists() && ! outputFolder.mkdir()) {
            throw new IllegalStateException("Failed to create new folder in user-directory");
        }
        return outputFolder;
    }


    // Concatenates all the text-pages from a single folder into one TXT file.
    // The concatenated file is stored in the tmp folder.
    protected static void concatPages(File txtFolder, String filenamePrefix) {
        Path output;
        try {
            output = Files.createTempFile(filenamePrefix, ".txt");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (Stream<Path> txtPages = Files.list(txtFolder.toPath())) {
            txtPages.sorted()
                    .flatMap(page -> {
                        try {
                            return Files.lines(page);
                        } catch (IOException e) {
                            return Stream.of("Error while reading text page at " + page);
                        }
                    })
                    .forEach(line -> {
                        try {
                            Files.writeString(output, line + System.lineSeparator(),
                                    StandardOpenOption.APPEND);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Helper function to walk through a directory-structure and process all files
    public static void processFilesInFolder(File folder, String srcDir, File outputFolder) throws IOException {
        Files.walkFileTree(folder.toPath(), new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                File file = path.toFile();
                StringBuilder prefixBuf = new StringBuilder(file.getName());

                Path parentDir = path.getParent();

                while (! srcDir.equals(parentDir.toFile().getName())) {
                    prefixBuf.insert(0, parentDir.toFile().getName() + "_");
                    parentDir = parentDir.getParent();
                }

                if (file.getName().endsWith(".pdf")) {
                    String prefix = prefixBuf.toString();
                    try {
                        pdfToTxt(file, prefix, outputFolder);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path path, IOException e) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });

    }
}
