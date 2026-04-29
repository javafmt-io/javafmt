package io.javafmt;

import java.util.List;

/**
 * Hand-picked Java sources for {@link JavafmtFormatBenchmark}. Sources are bundled as
 * string constants (no I/O at benchmark time) and were chosen to exercise distinct
 * printer hot paths: method-chain breaking, wide record components, and deeply
 * nested conditional / ternary chains. Inputs are large enough that printer cost
 * is a measurable fraction of total format time, but small enough that the whole
 * corpus formats well under one millisecond per strategy on a developer laptop.
 */
public final class BenchmarkCorpus {

    public static final String METHOD_CHAINS = """
        package demo;

        import java.util.Comparator;
        import java.util.List;
        import java.util.Optional;
        import java.util.stream.Collectors;
        import java.util.stream.Stream;

        class MethodChains {
            List<String> activeNames(final List<User> users) {
                return users.stream()
                        .filter(user -> user.isActive())
                        .filter(user -> user.getDepartment().isPresent())
                        .map(user -> user.getDepartment().get())
                        .map(department -> department.getName())
                        .distinct()
                        .sorted(Comparator.naturalOrder())
                        .limit(100)
                        .collect(Collectors.toList());
            }

            Optional<String> firstActiveName(final Stream<User> users) {
                return users.filter(user -> user.isActive())
                        .map(user -> user.getFullName())
                        .filter(name -> !name.isBlank())
                        .map(String::strip)
                        .map(String::toLowerCase)
                        .findFirst();
            }

            String summarize(final List<Order> orders) {
                return orders.stream()
                        .filter(order -> order.getStatus() == OrderStatus.SHIPPED)
                        .map(order -> order.getCustomer())
                        .map(customer -> customer.getDisplayName())
                        .sorted()
                        .distinct()
                        .map(name -> name.toUpperCase())
                        .collect(Collectors.joining(", ", "[", "]"));
            }
        }
        """;

    public static final String WIDE_RECORDS = """
        package demo;

        import java.time.Instant;
        import java.util.List;
        import java.util.Map;
        import java.util.Optional;

        record Customer(
            long id,
            String firstName,
            String lastName,
            String emailAddress,
            String phoneNumber,
            String streetAddress,
            String city,
            String region,
            String postalCode,
            String country,
            Instant createdAt,
            Instant lastLoginAt,
            List<String> roles,
            Map<String, String> preferences,
            Optional<String> referralCode
        ) {}

        record Order(
            long orderId,
            long customerId,
            String description,
            Instant placedAt,
            Instant shippedAt,
            String shippingAddress,
            String billingAddress,
            String paymentMethod,
            String trackingNumber,
            List<OrderLine> lines,
            Map<String, String> metadata
        ) {}

        record OrderLine(
            long lineId,
            long productId,
            String productName,
            int quantity,
            long unitPriceCents,
            long lineTotalCents,
            String currency,
            Optional<String> couponCode
        ) {}

        record AuditEvent(
            long eventId,
            String actor,
            String action,
            String resourceType,
            String resourceId,
            Instant occurredAt,
            String userAgent,
            String ipAddress,
            Map<String, String> attributes
        ) {}
        """;

    public static final String NESTED_CONDITIONALS = """
        package demo;

        class NestedConditionals {
            String classify(final int score, final boolean premium, final boolean verified) {
                return score >= 90
                        ? premium ? verified ? "premium-verified-elite" : "premium-elite" : "elite"
                        : score >= 75
                                ? premium ? verified ? "premium-verified-high" : "premium-high" : "high"
                                : score >= 50
                                        ? premium ? verified ? "premium-verified-mid" : "premium-mid" : "mid"
                                        : score >= 25
                                                ? premium ? "premium-low" : "low"
                                                : "none";
            }

            int route(final int kind, final int subKind, final boolean fast) {
                if (kind == 1) {
                    if (subKind == 1) {
                        if (fast) {
                            return 11;
                        } else {
                            return 12;
                        }
                    } else if (subKind == 2) {
                        if (fast) {
                            return 13;
                        } else {
                            return 14;
                        }
                    } else {
                        return 15;
                    }
                } else if (kind == 2) {
                    if (subKind == 1) {
                        return fast ? 21 : 22;
                    } else if (subKind == 2) {
                        return fast ? 23 : 24;
                    } else if (subKind == 3) {
                        return fast ? 25 : 26;
                    } else {
                        return 27;
                    }
                } else {
                    return 0;
                }
            }
        }
        """;

    public static final List<String> ALL = List.of(METHOD_CHAINS, WIDE_RECORDS, NESTED_CONDITIONALS);

    private BenchmarkCorpus() {}
}
