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
import static org.informiz.pdf.txt.Utils.createTxtPagesFolder;

public class ElasticSearchService {

    static final String MAPPING_RAW = """
            "{ mappings": {
                "properties": {
                      "origFile": { "type": "keyword" },
                      "page": { "type": "integer" },
                      "txt": { "type": "text" }
                    }
                  }
                }""";


    static final String MAPPING_PROCESSED_RECORDS = """
            "{ mappings": {
                "properties": {
                      "origFile": { "type": "keyword" },
                      "page": { "type": "integer" },
                      "people": { "type": "keyword" },
                      "dates": { "type": "date" },
                      "places": { "type": "keyword" },
                      "orgs": { "type": "keyword" },
                      "groups": { "type": "keyword" },
                      "txt": { "type": "text" }
                    }
                  }
                }""";
    public static final String PAGES_IDX = "pages";
    public static final String RECORDS_IDX = "records";

    static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.out.println("Please provide either a full path to a source-folder with PDF files or a full path " +
                    "to a source-folder with processed records");

            System.out.println("Example usage for converting PDF files to text and uploading the pages to ElasticSearch:");
            System.out.println("ES_LOCAL_API_KEY=je0i4rfvnho....ero8p9vk4jw==  " +
                    "java -cp path/to/app.jar " +
                    "org.informiz.pdf.txt.ElasticSearchService /full/path/to/source/folder/");

            System.out.println("Example usage for uploading already-processed records to ElasticSearch:");
            System.out.println("ES_LOCAL_API_KEY=je0i4rfvnho....ero8p9vk4jw==  " +
                    "java -cp path/to/app.jar " +
                    "org.informiz.pdf.txt.ElasticSearchService /full/path/to/source/folder/ --records");
            exit(1);
        }

        File srcFolder = new File(args[0]);
        if (! srcFolder.exists()) {
            System.out.println("Source-folder " + args[0] + " does not exist");
            exit(2);
        }

        System.out.println("Processing documents under " + args[0] + " and sub-folders...");

        boolean uploadRecords = args.length == 2 && "--records".equals(args[1]);
        String mappings = uploadRecords ? MAPPING_PROCESSED_RECORDS : MAPPING_RAW;
        String idx = uploadRecords ? RECORDS_IDX : PAGES_IDX;

        try {
            createIndexIfNotExists(idx, mappings);
            if (PAGES_IDX.equals(idx)) {
                File outputFolder = createTxtPagesFolder();
                Utils.processFilesInFolder(srcFolder, srcFolder.getName(), outputFolder);
                uploadToES(outputFolder, idx);
            } else {
                uploadToES(srcFolder, idx);
            }

            System.out.println("Done uploading to ElasticSearch");

        } catch (IOException e) {
            throw new RuntimeException("Unexpected error while processing files", e);
        }
    }


    public static void createIndexIfNotExists(String index, String mappings) throws IOException {
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
            createIndexRequest.setJsonEntity(mappings);
            createIndexRequest.setOptions(getReqOptions("application/json"));

            client.performRequest(createIndexRequest);
        }
    }



    public static void uploadToES(File srcFolder, String index) {
        try (Rest5Client client = getLocalClient()) {
            
            indexFilesInFolder(index, srcFolder, client);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void indexFilesInFolder(String index, File srcFolder, Rest5Client client) {
        StringBuffer leftoverPayload = new StringBuffer();

        indexFilesInFolder(index, srcFolder, leftoverPayload, client);

        if (!leftoverPayload.isEmpty()) {
            sendIndexRequest(index, leftoverPayload.toString(), client);
        }
    }

    private static void indexFilesInFolder(String index, File srcFolder, StringBuffer leftoverPayload, Rest5Client client) {

        StringBuffer jsonCommands;
        try (Stream<Path> filePaths = Files.list(srcFolder.toPath())) {
            if (PAGES_IDX.equals(index))
                jsonCommands = indexPages(index, filePaths, client);
            else
                jsonCommands = indexRecords(index, filePaths, client);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        leftoverPayload.append(jsonCommands);
        sendIfSufficientPayload(index, client, leftoverPayload);

        // TODO: sub-folders required for text-pages..?
        try (Stream<Path> filePaths = Files.list(srcFolder.toPath())) {
            filePaths.forEach(path -> {
                if (path.toFile().isDirectory()) {
                    indexFilesInFolder(index, path.toFile(), leftoverPayload, client);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int sendIndexRequest(String index, String jsonPayload, Rest5Client client) {
        Request request = new Request(
                "POST",
                "/" + index + "/_bulk");
        request.setJsonEntity(jsonPayload.toString());
        request.setOptions(getReqOptions("application/x-ndjson"));

        Response response;
        try {
            response = client.performRequest(request);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return response.getStatusCode();
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

    private static StringBuffer indexPages(String index, Stream<Path> filePaths, Rest5Client client) {
        Pattern filenamePattern = Pattern.compile("([^.]+)\\.pdf_page(\\d+)\\.pdf\\.txt");
        StringBuffer jsonPayload = new StringBuffer();

        filePaths.forEach(path ->  {

            File file = path.toFile();
            if (file.isDirectory())
                return;
            Matcher namePattern = filenamePattern.matcher(file.getName());
            if (! namePattern.find())
                return;

            String origFileName = namePattern.group(1);
            int pageNum = Integer.parseInt(namePattern.group(2));

            appendIndexCommand(index, file, jsonPayload, origFileName, pageNum);

            sendIfSufficientPayload(index, client, jsonPayload);

        });
        return jsonPayload;
    }

    private static void sendIfSufficientPayload(String index, Rest5Client client, StringBuffer jsonPayload) {
        if (jsonPayload.length() >= 10000) {
            sendIndexRequest(index, jsonPayload.toString(), client);
            jsonPayload.setLength(0);
        }
    }

    private static StringBuffer indexRecords(String index, Stream<Path> filePaths, Rest5Client client) {
        StringBuffer jsonPayload = new StringBuffer();

        filePaths.forEach(path ->  {

            File file = path.toFile();
            if (file.getName().endsWith(".record")){
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    appendIdxLine(jsonPayload, index, file.getName())
                            .append(reader.readLine())
                            .append(String.format("%n"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            sendIfSufficientPayload(index, client, jsonPayload);

        });

        return jsonPayload;
    }

    private static void appendIndexCommand(String index, File txtFile, StringBuffer jsonPayload, String origFileName, int pageNum) {
        String txt;
        try (BufferedReader reader = new BufferedReader(new FileReader(txtFile))) {
            txt = reader.lines()
                    .reduce((s, s2) -> s + "    " + s2)
                    .orElseGet(System::lineSeparator)
                    .replace('"', '\'');
            String fileName = txtFile.getName();

            appendIdxLine(jsonPayload, index, fileName)
                    .append("{ \"origFile\" : \"").append(origFileName)
                    .append("\",  \"page\" : \"").append(pageNum)
                    .append("\", \"txt\" : \"").append(txt).append("\"}")
                    .append(String.format("%n"));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static StringBuffer appendIdxLine(StringBuffer jsonPayload, String index, String fileName) {
        return jsonPayload.append("{ \"index\" : { \"_index\" : \"")
                .append(index).append("\", \"_id\" : \"")
                .append(fileName)
                .append("\" } }")
                .append(String.format("%n"));
    }
}
