package org.informiz.pdf.txt;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

/**
 * This test class contains "tests" for processing PDF files available under srxc/test/resources/docs
 * E.g., you can extract text from PDF files by placing them in the abovementioned folder and running the
 * 'extractText' test.
 */
class PdfDocumentServiceTest {

    @Test
    void extractText() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();

        File folder = new File(classLoader.getResource("docs/").getFile());

        for(File file : folder.listFiles()) {
            File pagesFolder = Files.createTempDirectory("split_" + file.getName() ).toFile();
            File txtFolder = Files.createTempDirectory("converted_" + file.getName()).toFile();

            PdfDocumentService.splitPdf(file, pagesFolder);

            for(File page : pagesFolder.listFiles())
                PdfDocumentService.extractText(page, txtFolder);

            // This part concatenates all the text-pages from a single PDF file into one TXT file.
            // The concatenated file is also stored in the tmp folder.
            Path output = Files.createTempFile(file.getName(), ".txt");

            try (Stream<Path> pages = Files.list(txtFolder.toPath())) {
                pages.sorted()
                        .flatMap(page -> {
                            try {
                                return Files.lines(page);
                            } catch (IOException e) {
                                return Stream.of("Error while reading text page at " + page);
                            }
                        })
                        .forEach(line -> {
                            try {
                                Files.write(output, (line + System.lineSeparator())
                                        .getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

            }

            }

    }
}