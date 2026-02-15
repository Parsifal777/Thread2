import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadTester {
    private final int numberOfRequests;
    private final String targetUrl;
    private final boolean useVirtualThreadsForClient;

    private final List<Long> responseTimes = new ArrayList<>();
    private int successfulRequests = 0;
    private int failedRequests = 0;

    public LoadTester(int numberOfRequests, String targetUrl, boolean useVirtualThreadsForClient) {
        this.numberOfRequests = numberOfRequests;
        this.targetUrl = targetUrl;
        this.useVirtualThreadsForClient = useVirtualThreadsForClient;
    }

    public void runTest() throws Exception {
        responseTimes.clear();
        successfulRequests = 0;
        failedRequests = 0;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        ExecutorService executor;
        if (useVirtualThreadsForClient) {
            executor = Executors.newVirtualThreadPerTaskExecutor();
            System.out.println("Клиент использует ВИРТУАЛЬНЫЕ потоки");
        } else {
            int clientThreads = Math.min(numberOfRequests, 500); // Ограничиваем для традиционных потоков
            executor = Executors.newFixedThreadPool(clientThreads);
            System.out.println("Клиент использует ТРАДИЦИОННЫЕ потоки (пул: " + clientThreads + ")");
        }

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            System.out.println("Запуск теста с " + numberOfRequests + " запросами...");
            Instant testStart = Instant.now();

            for (int i = 0; i < numberOfRequests; i++) {
                int requestId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    sendRequest(httpClient, requestId);
                }, executor);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            Instant testEnd = Instant.now();
            Duration totalTime = Duration.between(testStart, testEnd);

            printStatistics(totalTime);

        } finally {
            executor.shutdown();
        }
    }

    private void sendRequest(HttpClient client, int requestId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        Instant start = Instant.now();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Instant end = Instant.now();

            long responseTime = Duration.between(start, end).toMillis();

            synchronized (this) {
                responseTimes.add(responseTime);
                if (response.statusCode() == 200) {
                    successfulRequests++;
                } else {
                    failedRequests++;
                }
            }

        } catch (Exception e) {
            Instant end = Instant.now();
            long responseTime = Duration.between(start, end).toMillis();

            synchronized (this) {
                responseTimes.add(responseTime);
                failedRequests++;
            }
        }
    }

    private void printStatistics(Duration totalTime) {
        System.out.println("\nРЕЗУЛЬТАТЫ ТЕСТИРОВАНИЯ");
        System.out.println("Тип потоков клиента: " + (useVirtualThreadsForClient ? "Виртуальные" : "Традиционные"));
        System.out.println("Всего запросов: " + numberOfRequests);
        System.out.println("Успешных: " + successfulRequests);
        System.out.println("Ошибок: " + failedRequests);
        System.out.println("Общее время выполнения: " + totalTime.toMillis() + " мс");

        synchronized (this) {
            double avgResponseTime = responseTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
            System.out.printf("Среднее время ответа: %.2f мс\n", avgResponseTime);

            if (!responseTimes.isEmpty()) {
                responseTimes.sort(Long::compare);
                System.out.println("Медианное время: " + responseTimes.get(responseTimes.size() / 2) + " мс");
                System.out.println("95-й перцентиль: " + responseTimes.get((int)(responseTimes.size() * 0.95)) + " мс");
                System.out.println("Максимальное время: " + responseTimes.get(responseTimes.size() - 1) + " мс");
                System.out.println("Минимальное время: " + responseTimes.get(0) + " мс");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        runComparisonTest(10000);
    }

    private static void runComparisonTest(int requestCount) throws Exception {
        System.out.println("\nТЕСТ 1: ТРАДИЦИОННЫЕ ПОТОКИ НА КЛИЕНТЕ");
        LoadTester traditionalTester = new LoadTester(requestCount, "http://localhost:8080", false);
        traditionalTester.runTest();

        System.out.println("\nТЕСТ 2: ВИРТУАЛЬНЫЕ ПОТОКИ НА КЛИЕНТЕ");
        LoadTester virtualTester = new LoadTester(requestCount, "http://localhost:8080", true);
        virtualTester.runTest();
    }
}
