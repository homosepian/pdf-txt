package org.informiz.pdf.txt;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * This test class contains "tests" for processing PDF files available under srxc/test/resources/docs
 * E.g., you can extract text from PDF files by placing them in the abovementioned folder and running the
 * 'extractText' test.
 */

class PdfProcessingTest {

    @Test
    @Disabled
    void extractText() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();

        File srcFolder = new File(Objects.requireNonNull(classLoader.getResource("docs/")).getFile());

        Utils.processFilesInFolder(srcFolder, srcFolder.getName(), Utils::concatPages, null);
    }

    @Test
    @Disabled
    void loadToES() throws IOException {
        ElasticSearchService.createIndexIfNotExists("pages");

        ClassLoader classLoader = getClass().getClassLoader();
        File srcFolder = new File(Objects.requireNonNull(classLoader.getResource("docs/")).getFile());

        Utils.processFilesInFolder(srcFolder, srcFolder.getName(), ElasticSearchService::uploadToES, "pages");
    }
}