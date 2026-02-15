import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleHttpServer {
    private final HttpServer server;
    private final ExecutorService executorService;
    private final int port;

    public SimpleHttpServer(int port, boolean useVirtualThreads) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/", new RootHandler());

        if (useVirtualThreads) {
            this.executorService = Executors.newVirtualThreadPerTaskExecutor();
            System.out.println("Сервер использует ВИРТУАЛЬНЫЕ потоки");
        } else {
            int poolSize = 200;
            this.executorService = Executors.newFixedThreadPool(poolSize);
            System.out.println("Сервер использует ТРАДИЦИОННЫЕ потоки (пул: " + poolSize + ")");
        }

        server.setExecutor(executorService);
    }

    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Thread.sleep(10);

                String response = "Hello from server! Thread: " + Thread.currentThread();
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                String errorResponse = "Server error";
                exchange.sendResponseHeaders(500, errorResponse.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorResponse.getBytes());
                }
            }
        }
    }

    public void start() {
        server.start();
        System.out.println("Сервер запущен на порту " + port);
    }

    public void stop() {
        server.stop(0);
        executorService.shutdown();
        System.out.println("Сервер остановлен");
    }
}
