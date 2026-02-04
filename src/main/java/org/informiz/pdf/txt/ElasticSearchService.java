package org.informiz.pdf.txt;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.RequestOptions;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpHost;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.System.exit;

public class ElasticSearchService {

    static final String MAPPING_REQUEST = """
            "{ mappings": {
                "properties": {
                      "txt": { "type": "text" },
                      "origFile": { "type": "keyword" },
                      "page": { "type": "keyword" }
                    }
                  }
                }""";


    static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.out.println("Please provide full path to a source-folder with PDF files");
            System.out.println("Example usage:");
            System.out.println("ES_LOCAL_API_KEY=je0i4rfvnho....ero8p9vk4jw==  " +
                    "java -cp path/to/app.jar org.informiz.pdf.txt.ElasticSearchService /full/path/to/pdf/folder/");
            exit(1);
        }

        File srcFolder = new File(args[0]);
        if (! srcFolder.exists()) {
            System.out.println("Source-folder " + args[0] + " does not exist");
            exit(2);
        }

        System.out.println("Processing documents under " + args[0] + " and sub-folders...");

        try {
            createIndexIfNotExists("pages");
            Utils.processFilesInFolder(srcFolder, srcFolder.getName(), ElasticSearchService::uploadToES, "pages");
            System.out.println("Done uploading to ElasticSearch");
            System.out.println("You can find the extracted text-pages under " +
                    System.getProperty("java.io.tmpdir") +
                    " inside folders named 'converted_' followed by original PDF file name and random numbers");

        } catch (IOException e) {
            throw new RuntimeException("Unexpected error while processing files", e);
        }
    }


    public static void createIndexIfNotExists(String index) throws IOException {
        try (Rest5Client client = getLocalClient()) {
            Request indexExistsRequest = new Request(
                    "HEAD",
                    "/" + index + "?ignore_unavailable=false");
            indexExistsRequest.setOptions(getReqOptions(null));
            Response response = client.performRequest(indexExistsRequest);
            if (response.getStatusCode() == 200)
                return; // index already exists, nothing to do

            Request createIndexRequest = new Request(
                    "PUT",
                    "/" + index);
            createIndexRequest.setJsonEntity(MAPPING_REQUEST);
            createIndexRequest.setOptions(getReqOptions("application/json"));

            client.performRequest(createIndexRequest);
        }

    }

    public static int uploadToES(File srcFolder, String index) {
        Pattern filenamePattern = Pattern.compile("([^.]+)\\.pdf_page(\\d+)\\.pdf\\.txt");
        StringBuffer jsonPayload = new StringBuffer();

        try (Rest5Client client = getLocalClient();
             Stream<Path> txtFiles = Files.list(srcFolder.toPath())) {

            txtFiles.forEach(path ->  {

                File file = path.toFile();
                Matcher namePattern = filenamePattern.matcher(file.getName());
                if (! namePattern.find())
                    return;

                String origFileName = namePattern.group(1);
                int pageNum = Integer.parseInt(namePattern.group(2));

                appendIndexCommand(index, file, jsonPayload, origFileName, pageNum);
            });

            Request request = new Request(
                    "POST",
                    "/" + index + "/_bulk");
            request.setJsonEntity(jsonPayload.toString());
            request.setOptions(getReqOptions("application/x-ndjson"));

            Response response = client.performRequest(request);
            return response.getStatusCode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Rest5Client getLocalClient() {
        return Rest5Client
                .builder(new HttpHost("http", "localhost", 9200)).build();
    }

    private static RequestOptions.Builder getReqOptions(String contentType) {
        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        if (contentType != null)
            builder.addHeader("Content-Type", contentType);

        String esLocalApiKey = System.getenv("ES_LOCAL_API_KEY");
        if (esLocalApiKey == null || esLocalApiKey.isBlank())
            throw new IllegalStateException("Could not retrieve ElasticSearch API key, " +
                    "make sure the ES_LOCAL_API_KEY environment-variable is set to the value in the .env file " +
                    "under the elastic-start-local folder, and re-run the code.");

        return builder.addHeader("Authorization", "ApiKey " + esLocalApiKey);
    }

    private static void appendIndexCommand(String index, File txtFile, StringBuffer jsonPayload, String origFileName, int pageNum) {
        String txt;
        try (BufferedReader reader = new BufferedReader(new FileReader(txtFile))) {
            txt = reader.lines()
                    .reduce((s, s2) -> s + "    " + s2)
                    .orElseGet(System::lineSeparator)
                    .replace('"', '\'');
            jsonPayload.append("{ \"index\" : { \"_index\" : \"")
                    .append(index).append("\", \"_id\" : \"")
                    .append(txtFile.getName())
                    .append("\" } }")
                    .append(String.format("%n"))
                    .append("{ \"origFile\" : \"").append(origFileName)
                    .append("\",  \"page\" : \"").append(pageNum)
                    .append("\", \"txt\" : \"").append(txt).append("\"}")
                    .append(String.format("%n"));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
