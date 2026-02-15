public class VirtualThreadsDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("ДЕМОНСТРАЦИЯ ВИРТУАЛЬНЫХ ПОТОКОВ В JAVA\n");

        System.out.println("ТЕСТ 1: Сервер с ТРАДИЦИОННЫМИ потоками");
        runTestWithServer(false);

        System.out.println("\nТЕСТ 2: Сервер с ВИРТУАЛЬНЫМИ потоками");
        runTestWithServer(true);
    }

    private static void runTestWithServer(boolean useVirtualThreadsOnServer) throws Exception {
        SimpleHttpServer server = null;
        try {
            server = new SimpleHttpServer(8080, useVirtualThreadsOnServer);
            server.start();

            Thread.sleep(2000);

            LoadTester tester = new LoadTester(5000, "http://localhost:8080", true);
            tester.runTest();

        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }
}
