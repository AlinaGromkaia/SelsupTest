package org.example.crpt_api;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Semaphore semaphore;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.semaphore = new Semaphore(requestLimit);

        long period = switch (timeUnit) {
            case SECONDS -> 1L;
            case MINUTES -> 60L;
            case HOURS -> 3600L;
            default -> throw new IllegalArgumentException("Unsupported time unit");
        };
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::resetSemaphore, period, period, TimeUnit.SECONDS);
    }

    private void resetSemaphore() {
        semaphore.release(semaphore.availablePermits() - semaphore.getQueueLength());
    }

    public CompletableFuture<HttpResponse<String>> createDocument(Document document, String signature) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                semaphore.acquire();
                String requestBody = gson.toJson(new DocumentWrapper(document, signature));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://ismp.crpt.ru/api/v3/lk/documents/create"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } finally {
                semaphore.release();
            }
        }, executor);
    }

    private static class DocumentWrapper {
        private final Document document;
        private final String signature;

        public DocumentWrapper(Document document, String signature) {
            this.document = document;
            this.signature = signature;
        }
    }

    private static class Document {
        private String doc_id;
        private String doc_status;
        private DocType doc_type = DocType.LP_INTRODUCE_GOODS;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;

        public Document(String doc_id, String doc_status, boolean importRequest,
                        String owner_inn, String participant_inn, String producer_inn,
                        String production_date, String production_type, List<Product> products,
                        String reg_date, String reg_number) {
            this.doc_id = doc_id;
            this.doc_status = doc_status;
            this.importRequest = importRequest;
            this.owner_inn = owner_inn;
            this.participant_inn = participant_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.production_type = production_type;
            this.products = products;
            this.reg_date = reg_date;
            this.reg_number = reg_number;
        }
    }

    public enum DocType {
        LP_INTRODUCE_GOODS,
    }

    private static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;

        public Product(String certificate_document, String certificate_document_date,
                       String certificate_document_number, String owner_inn,
                       String producer_inn, String production_date, String tnved_code,
                       String uit_code, String uitu_code) {
            this.certificate_document = certificate_document;
            this.certificate_document_date = certificate_document_date;
            this.certificate_document_number = certificate_document_number;
            this.owner_inn = owner_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.tnved_code = tnved_code;
            this.uit_code = uit_code;
            this.uitu_code = uitu_code;
        }
    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);

        Product product = new Product(
                "certificate_document",
                "2024-04-03",
                "certificate_document_number",
                "owner_inn",
                "producer_inn",
                "2024-04-03",
                "tnved_code",
                "uit_code",
                "uitu_code"
        );

        List<Product> products = new ArrayList<>();
        products.add(product);

        Document document = new Document(
                "doc_id",
                "doc_status",
                true,
                "owner_inn",
                "participant_inn",
                "producer_inn",
                "2024-04-03",
                "production_type",
                products,
                "reg_date",
                "reg_number"
        );
        String signature = "signature";

        api.createDocument(document, signature).thenAccept(response -> {
            System.out.println("Response status code: " + response.statusCode());
            System.out.println("Response body: " + response.body());
        }).exceptionally(e -> {
            System.err.println("Error during API call: " + e.getMessage());
            return null;
        });
    }
}
