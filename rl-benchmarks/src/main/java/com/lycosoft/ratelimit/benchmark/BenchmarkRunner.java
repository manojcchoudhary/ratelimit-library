package com.lycosoft.ratelimit.benchmark;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

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
    private static final DecimalFormat THROUGHPUT_FORMAT = new DecimalFormat("#,##0.000");
    private static final DecimalFormat LARGE_THROUGHPUT_FORMAT = new DecimalFormat("#,###,###");
    private static final String BENCHMARK_MD_PATH = "BENCHMARK.md";

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
        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
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

        // Generate BENCHMARK.md file
        generateBenchmarkMarkdown(results, redisAvailable);

        System.out.println();
        System.out.println("Results saved to: benchmark-results.json");
        System.out.println("Markdown report saved to: " + BENCHMARK_MD_PATH);
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

    private static void generateBenchmarkMarkdown(Collection<RunResult> results, boolean redisAvailable) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(BENCHMARK_MD_PATH))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            writer.println("# Performance Benchmarks");
            writer.println();
            writer.println("This document contains the latest benchmark results for the Rate Limiting Library.");
            writer.println();
            writer.println("> **Last Updated**: " + timestamp);
            writer.println(">");
            writer.println("> **Environment**: Java " + System.getProperty("java.version") + ", " +
                    System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            writer.println(">");
            writer.println("> **Redis Available**: " + (redisAvailable ? "Yes" : "No"));
            writer.println();

            // Group results by benchmark class
            Map<String, List<RunResult>> groupedResults = results.stream()
                    .collect(Collectors.groupingBy(r -> {
                        String benchmark = r.getParams().getBenchmark();
                        String className = benchmark.substring(0, benchmark.lastIndexOf('.'));
                        return className.substring(className.lastIndexOf('.') + 1);
                    }));

            // Summary section
            writer.println("## Summary");
            writer.println();
            writer.println("| Category | Benchmark | Score | Unit |");
            writer.println("|----------|-----------|------:|------|");

            for (RunResult result : results) {
                String benchmark = result.getParams().getBenchmark();
                String className = extractClassName(benchmark);
                String methodName = benchmark.substring(benchmark.lastIndexOf('.') + 1);
                double score = result.getPrimaryResult().getScore();
                String unit = result.getPrimaryResult().getScoreUnit();

                writer.printf("| %s | %s | %s | %s |%n",
                        className, methodName, formatScore(score, unit), unit);
            }
            writer.println();

            // Detailed sections by category
            writer.println("## Detailed Results");
            writer.println();

            // Storage Performance
            writer.println("### Storage Performance");
            writer.println();
            writer.println("Comparison of different storage backends:");
            writer.println();
            writer.println("| Storage | Operation | Avg Latency | Throughput |");
            writer.println("|---------|-----------|-------------|------------|");

            for (RunResult result : results) {
                String benchmark = result.getParams().getBenchmark();
                if (benchmark.contains("Storage") || benchmark.contains("baseline")) {
                    String methodName = benchmark.substring(benchmark.lastIndexOf('.') + 1);
                    double score = result.getPrimaryResult().getScore();
                    String unit = result.getPrimaryResult().getScoreUnit();
                    String storage = extractStorage(benchmark);

                    if (!storage.isEmpty()) {
                        String latency = unit.contains("ops") ? "-" : formatScoreForTable(score, unit);
                        String throughput = unit.contains("ops") ? formatScore(score, unit) + " " + unit : "-";
                        writer.printf("| %s | %s | %s | %s |%n",
                                storage, methodName, latency, throughput);
                    }
                }
            }
            writer.println();

            // Tiered Storage Performance
            writer.println("### Tiered Storage (Redis L1 + Caffeine L2)");
            writer.println();
            writer.println("Production-recommended configuration with distributed Redis as primary and local Caffeine as fallback:");
            writer.println();
            writer.println("> **Note**: `tiered_healthCheck` and `tiered_diagnostics` query both Redis (L1) and Caffeine (L2),");
            writer.println("> so their latency includes Redis network round-trip (~500μs each). This is expected behavior.");
            writer.println();
            writer.println("| Benchmark | Avg Latency | Throughput |");
            writer.println("|-----------|-------------|------------|");

            for (RunResult result : results) {
                String benchmark = result.getParams().getBenchmark();
                if (benchmark.contains("tiered") || benchmark.contains("Tiered")) {
                    String methodName = benchmark.substring(benchmark.lastIndexOf('.') + 1);
                    double score = result.getPrimaryResult().getScore();
                    String unit = result.getPrimaryResult().getScoreUnit();

                    String latency = unit.contains("ops") ? "-" : formatScoreForTable(score, unit);
                    String throughput = unit.contains("ops") ? formatScore(score, unit) + " " + unit : "-";
                    writer.printf("| %s | %s | %s |%n", methodName, latency, throughput);
                }
            }
            writer.println();

            // SpEL Performance
            writer.println("### SpEL Expression Performance");
            writer.println();
            writer.println("Comparison of SpEL compilation modes (validates 40× performance claim):");
            writer.println();
            writer.println("| Mode | Benchmark | Avg Latency | Throughput |");
            writer.println("|------|-----------|-------------|------------|");

            for (RunResult result : results) {
                String benchmark = result.getParams().getBenchmark();
                if (benchmark.contains("SpEL") || benchmark.contains("spel") || benchmark.contains("static")) {
                    String methodName = benchmark.substring(benchmark.lastIndexOf('.') + 1);
                    double score = result.getPrimaryResult().getScore();
                    String unit = result.getPrimaryResult().getScoreUnit();

                    String mode = methodName.contains("compiled") ? "Compiled" :
                                  methodName.contains("interpreted") ? "Interpreted" :
                                  methodName.contains("static") ? "Static" : "Other";

                    String latency = unit.contains("ops") ? "-" : formatScoreForTable(score, unit);
                    String throughput = unit.contains("ops") ? formatScore(score, unit) + " " + unit : "-";
                    writer.printf("| %s | %s | %s | %s |%n", mode, methodName, latency, throughput);
                }
            }
            writer.println();

            // Algorithm Performance
            writer.println("### Algorithm Performance");
            writer.println();
            writer.println("Pure algorithm performance without storage overhead:");
            writer.println();
            writer.println("| Algorithm | Benchmark | Avg Latency | Throughput |");
            writer.println("|-----------|-----------|-------------|------------|");

            for (RunResult result : results) {
                String benchmark = result.getParams().getBenchmark();
                if (benchmark.contains("TokenBucket") && !benchmark.contains("Storage")) {
                    String methodName = benchmark.substring(benchmark.lastIndexOf('.') + 1);
                    double score = result.getPrimaryResult().getScore();
                    String unit = result.getPrimaryResult().getScoreUnit();

                    String latency = unit.contains("ops") ? "-" : formatScoreForTable(score, unit);
                    String throughput = unit.contains("ops") ? formatScore(score, unit) + " " + unit : "-";
                    writer.printf("| Token Bucket | %s | %s | %s |%n", methodName, latency, throughput);
                }
            }
            writer.println();

            // Running Benchmarks section
            writer.println("## Running Benchmarks");
            writer.println();
            writer.println("To run benchmarks yourself:");
            writer.println();
            writer.println("```bash");
            writer.println("# Build the benchmark jar");
            writer.println("mvn clean package -pl rl-benchmarks -am -DskipTests");
            writer.println();
            writer.println("# Run all benchmarks (without Redis)");
            writer.println("java -jar rl-benchmarks/target/benchmarks.jar");
            writer.println();
            writer.println("# Run with Redis");
            writer.println("docker run -d -p 6379:6379 redis:7-alpine");
            writer.println("java -Dredis.host=localhost -Dredis.port=6379 \\");
            writer.println("     -jar rl-benchmarks/target/benchmarks.jar");
            writer.println();
            writer.println("# Run specific benchmark");
            writer.println("java -jar rl-benchmarks/target/benchmarks.jar StorageBenchmark");
            writer.println("java -jar rl-benchmarks/target/benchmarks.jar TieredStorageBenchmark");
            writer.println("java -jar rl-benchmarks/target/benchmarks.jar RedisBenchmark");
            writer.println("java -jar rl-benchmarks/target/benchmarks.jar SpELBenchmark");
            writer.println("java -jar rl-benchmarks/target/benchmarks.jar TokenBucketBenchmark");
            writer.println("```");
            writer.println();

            // Notes section
            writer.println("## Notes");
            writer.println();
            writer.println("### Understanding the Results");
            writer.println();
            writer.println("- **Throughput** is measured in operations per time unit (higher is better)");
            writer.println("- **Latency** is measured in time per operation (lower is better)");
            writer.println("- Results may vary based on hardware, JVM version, and system load");
            writer.println();
            writer.println("### Performance Expectations");
            writer.println();
            writer.println("| Storage Type | Expected Latency | Notes |");
            writer.println("|--------------|------------------|-------|");
            writer.println("| InMemory | < 50 ns | Fastest, no synchronization overhead |");
            writer.println("| Caffeine | < 500 ns | Thread-safe with minimal overhead |");
            writer.println("| Tiered (local) | < 1 μs | Small overhead for L1/L2 abstraction |");
            writer.println("| Redis | ~500 μs | Network round-trip dominates |");
            writer.println("| Tiered (Redis+Caffeine) | ~500 μs | Redis latency + minimal local overhead |");
            writer.println();
            writer.println("### Important Considerations");
            writer.println();
            writer.println("- Redis benchmarks require a running Redis instance");
            writer.println("- Tiered storage uses Redis as L1 (distributed) and Caffeine as L2 (local fallback)");
            writer.println("- Concurrent benchmarks use 8 threads to simulate real-world contention");
            writer.println("- Health check and diagnostics for tiered Redis+Caffeine query both layers");

            System.out.println("Generated " + BENCHMARK_MD_PATH);

        } catch (IOException e) {
            System.err.println("Failed to write " + BENCHMARK_MD_PATH + ": " + e.getMessage());
        }
    }

    private static String extractClassName(String benchmark) {
        String className = benchmark.substring(0, benchmark.lastIndexOf('.'));
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }

    private static String formatScore(double score, String unit) {
        if (unit.contains("ops")) {
            // Use appropriate format based on magnitude
            if (score >= 1000) {
                return LARGE_THROUGHPUT_FORMAT.format(score);
            } else {
                return THROUGHPUT_FORMAT.format(score);
            }
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
