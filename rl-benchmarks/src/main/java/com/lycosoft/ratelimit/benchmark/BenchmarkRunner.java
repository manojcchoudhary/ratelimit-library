package com.lycosoft.ratelimit.benchmark;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.text.DecimalFormat;
import java.util.Collection;

/**
 * Unified benchmark runner for all rate limit performance tests.
 *
 * <p><b>Usage:</b>
 * <pre>
 * # Run all benchmarks with formatted output
 * java -cp benchmarks.jar com.lycosoft.ratelimit.benchmark.BenchmarkRunner
 *
 * # Run with Redis (start Redis first)
 * docker run -d -p 6379:6379 redis:7-alpine
 * java -Dredis.host=localhost -Dredis.port=6379 \
 *      -cp benchmarks.jar com.lycosoft.ratelimit.benchmark.BenchmarkRunner
 *
 * # Run specific benchmark suite
 * java -jar benchmarks.jar TokenBucketBenchmark
 * java -jar benchmarks.jar SpELBenchmark
 * java -jar benchmarks.jar StorageBenchmark
 * java -jar benchmarks.jar TieredStorageBenchmark
 * java -jar benchmarks.jar RedisBenchmark
 * </pre>
 *
 * <p><b>Expected Results:</b>
 * <pre>
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                    RATE LIMIT LIBRARY BENCHMARK RESULTS                      ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║ Storage Type      │ Algorithm      │ P99 Latency  │ Throughput (ops/s)      ║
 * ╠═══════════════════╪════════════════╪══════════════╪═════════════════════════╣
 * ║ Local (Caffeine)  │ Token Bucket   │ 0.04 ms      │ 12,500,000              ║
 * ║ Local (Caffeine)  │ Sliding Window │ 0.08 ms      │ 8,200,000               ║
 * ║ Local (InMemory)  │ Token Bucket   │ 0.02 ms      │ 25,000,000              ║
 * ║ Tiered (Caf+Caf)  │ Token Bucket   │ 0.05 ms      │ 10,000,000              ║
 * ║ Distributed(Redis)│ Token Bucket   │ 1.20 ms      │ 45,000                  ║
 * ║ Tiered (Redis+Caf)│ Token Bucket   │ 1.25 ms      │ 42,000                  ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║ SpEL Performance                                                             ║
 * ╠═══════════════════╪════════════════╪══════════════╪═════════════════════════╣
 * ║ Static Key        │ No SpEL        │ 0.001 ms     │ 100,000,000             ║
 * ║ Compiled SpEL     │ Cached         │ 0.002 ms     │ 50,000,000              ║
 * ║ Interpreted SpEL  │ Parse Each     │ 0.080 ms     │ 1,250,000               ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 * </pre>
 */
public class BenchmarkRunner {

    private static final DecimalFormat LATENCY_FORMAT = new DecimalFormat("#,##0.000");
    private static final DecimalFormat THROUGHPUT_FORMAT = new DecimalFormat("#,###,###");

    public static void main(String[] args) throws RunnerException {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              RATE LIMIT LIBRARY - PERFORMANCE BENCHMARKS                     ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Running comprehensive benchmarks... This may take 10-15 minutes.             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Check Redis availability
        boolean redisAvailable = checkRedisAvailable();
        if (!redisAvailable) {
            System.out.println("⚠️  Redis not available. Redis benchmarks will be skipped.");
            System.out.println("   To enable: docker run -d -p 6379:6379 redis:7-alpine");
            System.out.println();
        }

        // Build benchmark options
        OptionsBuilder optionsBuilder = new OptionsBuilder()
                .warmupIterations(2)
                .measurementIterations(3)
                .forks(1)
                .resultFormat(ResultFormatType.JSON)
                .result("benchmark-results.json");

        // Include all benchmarks
        optionsBuilder.include(TokenBucketBenchmark.class.getSimpleName());
        optionsBuilder.include(SpELBenchmark.class.getSimpleName());
        optionsBuilder.include(StorageBenchmark.class.getSimpleName());
        optionsBuilder.include(TieredStorageBenchmark.class.getSimpleName());

        if (redisAvailable) {
            optionsBuilder.include(RedisBenchmark.class.getSimpleName());
        }

        Options options = optionsBuilder.build();

        // Run benchmarks
        Collection<RunResult> results = new Runner(options).run();

        // Print formatted results
        printFormattedResults(results);

        // Print summary table for README
        printReadmeTable(results);
    }

    private static boolean checkRedisAvailable() {
        String redisHost = System.getProperty("redis.host", "localhost");
        int redisPort = Integer.parseInt(System.getProperty("redis.port", "6379"));

        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(redisHost, redisPort), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void printFormattedResults(Collection<RunResult> results) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         BENCHMARK RESULTS SUMMARY                            ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║ %-40s │ %12s │ %15s ║%n", "Benchmark", "Score", "Unit");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════╣");

        for (RunResult result : results) {
            String benchmark = result.getParams().getBenchmark();
            String shortName = benchmark.substring(benchmark.lastIndexOf('.') + 1);
            double score = result.getPrimaryResult().getScore();
            String unit = result.getPrimaryResult().getScoreUnit();

            System.out.printf("║ %-40s │ %12s │ %15s ║%n",
                    truncate(shortName, 40),
                    formatScore(score, unit),
                    unit);
        }

        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
    }

    private static void printReadmeTable(Collection<RunResult> results) {
        System.out.println();
        System.out.println("## Benchmark Results (for README.md)");
        System.out.println();
        System.out.println("| Storage Type | Algorithm | P99 Latency | Throughput |");
        System.out.println("|--------------|-----------|-------------|------------|");

        for (RunResult result : results) {
            String benchmark = result.getParams().getBenchmark();
            double score = result.getPrimaryResult().getScore();
            String unit = result.getPrimaryResult().getScoreUnit();

            // Parse benchmark name to extract storage and algorithm
            String storage = extractStorage(benchmark);
            String algorithm = extractAlgorithm(benchmark);
            String formattedScore = formatScoreForTable(score, unit);

            if (!storage.isEmpty()) {
                System.out.printf("| %s | %s | %s | - |%n",
                        storage, algorithm, formattedScore);
            }
        }

        System.out.println();
        System.out.println("Results saved to: benchmark-results.json");
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }

    private static String formatScore(double score, String unit) {
        if (unit.contains("ops")) {
            return THROUGHPUT_FORMAT.format(score);
        } else {
            return LATENCY_FORMAT.format(score);
        }
    }

    private static String formatScoreForTable(double score, String unit) {
        if (unit.contains("ns")) {
            return LATENCY_FORMAT.format(score / 1000.0) + " μs";
        } else if (unit.contains("us") || unit.contains("μs")) {
            return LATENCY_FORMAT.format(score) + " μs";
        } else if (unit.contains("ms")) {
            return LATENCY_FORMAT.format(score) + " ms";
        } else {
            return LATENCY_FORMAT.format(score) + " " + unit;
        }
    }

    private static String extractStorage(String benchmark) {
        if (benchmark.contains("caffeine") || benchmark.contains("Caffeine")) {
            return "Caffeine";
        } else if (benchmark.contains("redis") || benchmark.contains("Redis")) {
            return "Redis";
        } else if (benchmark.contains("tiered") || benchmark.contains("Tiered")) {
            return "Tiered";
        } else if (benchmark.contains("inmemory") || benchmark.contains("InMemory")) {
            return "InMemory";
        }
        return "";
    }

    private static String extractAlgorithm(String benchmark) {
        if (benchmark.contains("tokenBucket") || benchmark.contains("TokenBucket")) {
            return "Token Bucket";
        } else if (benchmark.contains("slidingWindow") || benchmark.contains("SlidingWindow")) {
            return "Sliding Window";
        } else if (benchmark.contains("SpEL") || benchmark.contains("spel")) {
            return "SpEL";
        }
        return "General";
    }
}
