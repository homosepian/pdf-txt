package org.informiz.pdf.txt;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class Utils {
    // Split a PDF file into pages and extract the text from each page.
    // Resulting files/folders are created in the default temp folder.
    // Returns the folder containing the text pages.
    protected static File pdfToTxt(File pdfFile, String filenamePrefix) throws IOException {
        File pagesFolder = Files.createTempDirectory("split_" + filenamePrefix).toFile();
        File txtFolder = Files.createTempDirectory("converted_" + filenamePrefix).toFile();

        PdfDocumentService.splitPdf(pdfFile, pagesFolder, filenamePrefix);

        try (Stream<Path> pdfPages = Files.list(pagesFolder.toPath())) {
            pdfPages.forEach(path -> PdfDocumentService.extractText(path.toFile(), txtFolder));
        }
        return txtFolder;
    }

    // Concatenates all the text-pages from a single PDF file into one TXT file.
    // The concatenated file is also stored in the tmp folder.
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
    public static void processFilesInFolder(File folder, String srcDir,
                                            BiConsumer<File, String> txtConsumer, String strParam) throws IOException {
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
                String prefix = prefixBuf.toString();

                if (file.getName().endsWith(".pdf")) {
                    try {
                        File txtFolder = pdfToTxt(file, prefix);
                        String param = (strParam == null) ? prefix : strParam;
                        txtConsumer.accept(txtFolder, param);
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
