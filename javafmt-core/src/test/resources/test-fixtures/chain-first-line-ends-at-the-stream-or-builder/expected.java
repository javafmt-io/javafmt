class Fixture {

    OkHttpClient client() {
        return new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    Order shipped() {
        return repository.findById(id).orElseThrow().toBuilder()
                .status(Status.SHIPPED)
                .shippedAt(clock.instant())
                .carrier(carrier)
                .trackingNumber(trackingNumber)
                .build();
    }

    String describe() {
        return new StringBuilder()
                .append("order ")
                .append(order.id())
                .append(" for ")
                .append(order.customerId())
                .append(" totalling ")
                .append(order.total())
                .toString();
    }

    void register() {
        clients.register(new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build());
    }

    OkHttpClient small() {
        return new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    List<Path> directories(final String[] arguments) {
        return Arrays.stream(arguments)
                .skip(1)
                .map(Path::of)
                .filter(Files::isDirectory)
                .filter(Files::isReadable)
                .sorted(BY_NAME)
                .distinct()
                .limit(MAXIMUM)
                .toList();
    }

    Process start() {
        return new ProcessBuilder(command)
                .start();
    }
}
