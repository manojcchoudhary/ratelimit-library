# Performance Benchmarks

This document contains the latest benchmark results for the Rate Limiting Library.

> **Last Updated**: 2026-01-03 08:52:42
>
> **Environment**: Java 17.0.14, Mac OS X aarch64
>
> **Redis Available**: Yes

## Summary

| Category | Benchmark | Score | Unit |
|----------|-----------|------:|------|
| RedisBenchmark | baseline_caffeine | 2.867 | ops/us |
| RedisBenchmark | redis_batch_100_operations | 18.851 | ops/ms |
| RedisBenchmark | redis_concurrent_differentKeys | 0.040 | ops/us |
| RedisBenchmark | redis_concurrent_sameKey | 0.039 | ops/us |
| RedisBenchmark | redis_diagnostics | 0.022 | ops/us |
| RedisBenchmark | redis_healthCheck | 0.024 | ops/us |
| RedisBenchmark | redis_randomKeys | 0.018 | ops/us |
| RedisBenchmark | redis_singleKey_slidingWindow | 0.019 | ops/us |
| RedisBenchmark | redis_singleKey_tokenBucket | 0.018 | ops/us |
| RedisBenchmark | tiered_batch_100_operations | 17.895 | ops/ms |
| RedisBenchmark | tiered_circuitBreaker_stateCheck | 1.008 | ops/ns |
| RedisBenchmark | tiered_concurrent_differentKeys | 0.039 | ops/us |
| RedisBenchmark | tiered_concurrent_sameKey | 0.041 | ops/us |
| RedisBenchmark | tiered_diagnostics | 0.011 | ops/us |
| RedisBenchmark | tiered_healthCheck | 0.010 | ops/us |
| RedisBenchmark | tiered_redis_caffeine_randomKeys | 0.018 | ops/us |
| RedisBenchmark | tiered_redis_caffeine_slidingWindow | 0.018 | ops/us |
| RedisBenchmark | tiered_redis_caffeine_tokenBucket | 0.018 | ops/us |
| SpELBenchmark | cacheHit_sameExpression | 0.007 | ops/ns |
| SpELBenchmark | cacheMiss_differentExpressions | 5.360 | ops/us |
| SpELBenchmark | compiledSpEL_headerAccess | 7.496 | ops/us |
| SpELBenchmark | compiledSpEL_simpleVariable | 14.871 | ops/us |
| SpELBenchmark | compiledSpEL_userPlusIp | 7.914 | ops/us |
| SpELBenchmark | complexSpEL_multiVariable | 4.436 | ops/us |
| SpELBenchmark | interpretedSpEL_cachedExpression | 5.931 | ops/us |
| SpELBenchmark | interpretedSpEL_parseEveryTime | 1.685 | ops/us |
| SpELBenchmark | staticKey_noSpEL | 0.015 | ops/ns |
| StorageBenchmark | batch_100_tryAcquire | 3.458 | ops/us |
| StorageBenchmark | batch_100_tryAcquire | 71.735 | ops/us |
| StorageBenchmark | concurrent_differentKeys | 0.003 | ops/ns |
| StorageBenchmark | concurrent_differentKeys | 0.047 | ops/ns |
| StorageBenchmark | concurrent_sameKey | 0.002 | ops/ns |
| StorageBenchmark | concurrent_sameKey | 0.009 | ops/ns |
| StorageBenchmark | getDiagnostics | 16.954 | ops/us |
| StorageBenchmark | getDiagnostics | 50.381 | ops/us |
| StorageBenchmark | healthCheck | 1.726 | ops/ns |
| StorageBenchmark | healthCheck | 1.601 | ops/ns |
| StorageBenchmark | randomKey_getState | 0.275 | ops/ns |
| StorageBenchmark | randomKey_getState | 0.184 | ops/ns |
| StorageBenchmark | randomKey_tryAcquire | 0.002 | ops/ns |
| StorageBenchmark | randomKey_tryAcquire | 0.013 | ops/ns |
| StorageBenchmark | reset_singleKey | 1.576 | ops/us |
| StorageBenchmark | reset_singleKey | 17.488 | ops/us |
| StorageBenchmark | singleKey_getState | 0.567 | ops/ns |
| StorageBenchmark | singleKey_getState | 0.315 | ops/ns |
| StorageBenchmark | singleKey_tryAcquire_slidingWindow | 0.003 | ops/ns |
| StorageBenchmark | singleKey_tryAcquire_slidingWindow | 0.025 | ops/ns |
| StorageBenchmark | singleKey_tryAcquire_tokenBucket | 0.003 | ops/ns |
| StorageBenchmark | singleKey_tryAcquire_tokenBucket | 0.030 | ops/ns |
| TieredStorageBenchmark | baseline_caffeineOnly | 3.099 | ops/us |
| TieredStorageBenchmark | baseline_inMemoryOnly | 29.413 | ops/us |
| TieredStorageBenchmark | circuitBreaker_closedState | 3.029 | ops/us |
| TieredStorageBenchmark | diagnostics_retrieval | 0.006 | ops/ns |
| TieredStorageBenchmark | healthCheck_overhead | 0.001 | ops/ns |
| TieredStorageBenchmark | tiered_caffeine_caffeine_slidingWindow | 2.981 | ops/us |
| TieredStorageBenchmark | tiered_caffeine_caffeine_tokenBucket | 2.967 | ops/us |
| TieredStorageBenchmark | tiered_concurrent_differentKeys | 2.783 | ops/us |
| TieredStorageBenchmark | tiered_concurrent_sameKey | 2.301 | ops/us |
| TieredStorageBenchmark | tiered_inmemory_inmemory_slidingWindow | 22.242 | ops/us |
| TieredStorageBenchmark | tiered_inmemory_inmemory_tokenBucket | 26.663 | ops/us |
| TieredStorageBenchmark | tiered_l1Failure_l2Fallback | 0.562 | ops/us |
| TieredStorageBenchmark | tiered_randomKeys | 2.613 | ops/us |
| TokenBucketBenchmark | burstScenario | 0.019 | ops/ns |
| TokenBucketBenchmark | caffeineStorage_tryAcquire | 1.613 | ops/us |
| TokenBucketBenchmark | concurrentKeys_tryAcquire | 1.943 | ops/us |
| TokenBucketBenchmark | pureAlgorithm_tryConsume | 0.210 | ops/ns |
| RedisBenchmark | baseline_caffeine | 0.357 | us/op |
| RedisBenchmark | redis_batch_100_operations | 0.056 | ms/op |
| RedisBenchmark | redis_concurrent_differentKeys | 189.491 | us/op |
| RedisBenchmark | redis_concurrent_sameKey | 190.101 | us/op |
| RedisBenchmark | redis_diagnostics | 44.921 | us/op |
| RedisBenchmark | redis_healthCheck | 42.014 | us/op |
| RedisBenchmark | redis_latency_p99 | 0.056 | ms/op |
| RedisBenchmark | redis_randomKeys | 52.290 | us/op |
| RedisBenchmark | redis_singleKey_slidingWindow | 50.266 | us/op |
| RedisBenchmark | redis_singleKey_tokenBucket | 52.655 | us/op |
| RedisBenchmark | tiered_batch_100_operations | 0.056 | ms/op |
| RedisBenchmark | tiered_circuitBreaker_stateCheck | 0.995 | ns/op |
| RedisBenchmark | tiered_concurrent_differentKeys | 200.498 | us/op |
| RedisBenchmark | tiered_concurrent_sameKey | 199.161 | us/op |
| RedisBenchmark | tiered_diagnostics | 88.691 | us/op |
| RedisBenchmark | tiered_healthCheck | 104.933 | us/op |
| RedisBenchmark | tiered_redis_caffeine_randomKeys | 56.743 | us/op |
| RedisBenchmark | tiered_redis_caffeine_slidingWindow | 50.741 | us/op |
| RedisBenchmark | tiered_redis_caffeine_tokenBucket | 52.386 | us/op |
| SpELBenchmark | cacheHit_sameExpression | 126.268 | ns/op |
| SpELBenchmark | cacheMiss_differentExpressions | 0.187 | us/op |
| SpELBenchmark | compiledSpEL_headerAccess | 0.135 | us/op |
| SpELBenchmark | compiledSpEL_simpleVariable | 0.063 | us/op |
| SpELBenchmark | compiledSpEL_userPlusIp | 0.133 | us/op |
| SpELBenchmark | complexSpEL_multiVariable | 0.229 | us/op |
| SpELBenchmark | interpretedSpEL_cachedExpression | 0.169 | us/op |
| SpELBenchmark | interpretedSpEL_parseEveryTime | 0.577 | us/op |
| SpELBenchmark | staticKey_noSpEL | 67.996 | ns/op |
| StorageBenchmark | batch_100_tryAcquire | 0.278 | us/op |
| StorageBenchmark | batch_100_tryAcquire | 0.014 | us/op |
| StorageBenchmark | concurrent_differentKeys | 2,880.422 | ns/op |
| StorageBenchmark | concurrent_differentKeys | 123.487 | ns/op |
| StorageBenchmark | concurrent_sameKey | 3,359.812 | ns/op |
| StorageBenchmark | concurrent_sameKey | 871.740 | ns/op |
| StorageBenchmark | getDiagnostics | 0.060 | us/op |
| StorageBenchmark | getDiagnostics | 0.020 | us/op |
| StorageBenchmark | healthCheck | 0.615 | ns/op |
| StorageBenchmark | healthCheck | 0.578 | ns/op |
| StorageBenchmark | randomKey_getState | 3.632 | ns/op |
| StorageBenchmark | randomKey_getState | 5.393 | ns/op |
| StorageBenchmark | randomKey_tryAcquire | 581.081 | ns/op |
| StorageBenchmark | randomKey_tryAcquire | 76.912 | ns/op |
| StorageBenchmark | reset_singleKey | 0.608 | us/op |
| StorageBenchmark | reset_singleKey | 0.056 | us/op |
| StorageBenchmark | singleKey_getState | 1.748 | ns/op |
| StorageBenchmark | singleKey_getState | 3.165 | ns/op |
| StorageBenchmark | singleKey_tryAcquire_slidingWindow | 317.593 | ns/op |
| StorageBenchmark | singleKey_tryAcquire_slidingWindow | 38.777 | ns/op |
| StorageBenchmark | singleKey_tryAcquire_tokenBucket | 310.113 | ns/op |
| StorageBenchmark | singleKey_tryAcquire_tokenBucket | 33.216 | ns/op |
| TieredStorageBenchmark | baseline_caffeineOnly | 0.311 | us/op |
| TieredStorageBenchmark | baseline_inMemoryOnly | 0.034 | us/op |
| TieredStorageBenchmark | circuitBreaker_closedState | 0.342 | us/op |
| TieredStorageBenchmark | diagnostics_retrieval | 190.031 | ns/op |
| TieredStorageBenchmark | healthCheck_overhead | 1,439.342 | ns/op |
| TieredStorageBenchmark | tiered_caffeine_caffeine_slidingWindow | 0.335 | us/op |
| TieredStorageBenchmark | tiered_caffeine_caffeine_tokenBucket | 0.359 | us/op |
| TieredStorageBenchmark | tiered_concurrent_differentKeys | 2.881 | us/op |
| TieredStorageBenchmark | tiered_concurrent_sameKey | 3.295 | us/op |
| TieredStorageBenchmark | tiered_inmemory_inmemory_slidingWindow | 0.045 | us/op |
| TieredStorageBenchmark | tiered_inmemory_inmemory_tokenBucket | 0.039 | us/op |
| TieredStorageBenchmark | tiered_l1Failure_l2Fallback | 1.748 | us/op |
| TieredStorageBenchmark | tiered_randomKeys | 0.356 | us/op |
| TokenBucketBenchmark | burstScenario | 52.574 | ns/op |
| TokenBucketBenchmark | caffeineStorage_tryAcquire | 0.615 | us/op |
| TokenBucketBenchmark | concurrentKeys_tryAcquire | 1.930 | us/op |
| TokenBucketBenchmark | pureAlgorithm_tryConsume | 4.786 | ns/op |

## Detailed Results

### Storage Performance

Comparison of different storage backends:

| Storage | Operation | Avg Latency | Throughput |
|---------|-----------|-------------|------------|
| Caffeine | baseline_caffeine | - | 2.867 ops/us |
| Caffeine | baseline_caffeineOnly | - | 3.099 ops/us |
| Tiered | baseline_inMemoryOnly | - | 29.413 ops/us |
| Tiered | circuitBreaker_closedState | - | 3.029 ops/us |
| Tiered | diagnostics_retrieval | - | 0.006 ops/ns |
| Tiered | healthCheck_overhead | - | 0.001 ops/ns |
| Caffeine | tiered_caffeine_caffeine_slidingWindow | - | 2.981 ops/us |
| Caffeine | tiered_caffeine_caffeine_tokenBucket | - | 2.967 ops/us |
| Tiered | tiered_concurrent_differentKeys | - | 2.783 ops/us |
| Tiered | tiered_concurrent_sameKey | - | 2.301 ops/us |
| Tiered | tiered_inmemory_inmemory_slidingWindow | - | 22.242 ops/us |
| Tiered | tiered_inmemory_inmemory_tokenBucket | - | 26.663 ops/us |
| Tiered | tiered_l1Failure_l2Fallback | - | 0.562 ops/us |
| Tiered | tiered_randomKeys | - | 2.613 ops/us |
| Caffeine | caffeineStorage_tryAcquire | - | 1.613 ops/us |
| Caffeine | baseline_caffeine | 0.357 μs | - |
| Caffeine | baseline_caffeineOnly | 0.311 μs | - |
| Tiered | baseline_inMemoryOnly | 0.034 μs | - |
| Tiered | circuitBreaker_closedState | 0.342 μs | - |
| Tiered | diagnostics_retrieval | 0.190 μs | - |
| Tiered | healthCheck_overhead | 1.439 μs | - |
| Caffeine | tiered_caffeine_caffeine_slidingWindow | 0.335 μs | - |
| Caffeine | tiered_caffeine_caffeine_tokenBucket | 0.359 μs | - |
| Tiered | tiered_concurrent_differentKeys | 2.881 μs | - |
| Tiered | tiered_concurrent_sameKey | 3.295 μs | - |
| Tiered | tiered_inmemory_inmemory_slidingWindow | 0.045 μs | - |
| Tiered | tiered_inmemory_inmemory_tokenBucket | 0.039 μs | - |
| Tiered | tiered_l1Failure_l2Fallback | 1.748 μs | - |
| Tiered | tiered_randomKeys | 0.356 μs | - |
| Caffeine | caffeineStorage_tryAcquire | 0.615 μs | - |

### Tiered Storage (Redis L1 + Caffeine L2)

Production-recommended configuration with distributed Redis as primary and local Caffeine as fallback:

> **Note**: `tiered_healthCheck` and `tiered_diagnostics` query both Redis (L1) and Caffeine (L2),
> so their latency includes Redis network round-trip (~500μs each). This is expected behavior.

| Benchmark | Avg Latency | Throughput |
|-----------|-------------|------------|
| tiered_batch_100_operations | - | 17.895 ops/ms |
| tiered_circuitBreaker_stateCheck | - | 1.008 ops/ns |
| tiered_concurrent_differentKeys | - | 0.039 ops/us |
| tiered_concurrent_sameKey | - | 0.041 ops/us |
| tiered_diagnostics | - | 0.011 ops/us |
| tiered_healthCheck | - | 0.010 ops/us |
| tiered_redis_caffeine_randomKeys | - | 0.018 ops/us |
| tiered_redis_caffeine_slidingWindow | - | 0.018 ops/us |
| tiered_redis_caffeine_tokenBucket | - | 0.018 ops/us |
| baseline_caffeineOnly | - | 3.099 ops/us |
| baseline_inMemoryOnly | - | 29.413 ops/us |
| circuitBreaker_closedState | - | 3.029 ops/us |
| diagnostics_retrieval | - | 0.006 ops/ns |
| healthCheck_overhead | - | 0.001 ops/ns |
| tiered_caffeine_caffeine_slidingWindow | - | 2.981 ops/us |
| tiered_caffeine_caffeine_tokenBucket | - | 2.967 ops/us |
| tiered_concurrent_differentKeys | - | 2.783 ops/us |
| tiered_concurrent_sameKey | - | 2.301 ops/us |
| tiered_inmemory_inmemory_slidingWindow | - | 22.242 ops/us |
| tiered_inmemory_inmemory_tokenBucket | - | 26.663 ops/us |
| tiered_l1Failure_l2Fallback | - | 0.562 ops/us |
| tiered_randomKeys | - | 2.613 ops/us |
| tiered_batch_100_operations | 0.056 ms | - |
| tiered_circuitBreaker_stateCheck | 0.001 μs | - |
| tiered_concurrent_differentKeys | 200.498 μs | - |
| tiered_concurrent_sameKey | 199.161 μs | - |
| tiered_diagnostics | 88.691 μs | - |
| tiered_healthCheck | 104.933 μs | - |
| tiered_redis_caffeine_randomKeys | 56.743 μs | - |
| tiered_redis_caffeine_slidingWindow | 50.741 μs | - |
| tiered_redis_caffeine_tokenBucket | 52.386 μs | - |
| baseline_caffeineOnly | 0.311 μs | - |
| baseline_inMemoryOnly | 0.034 μs | - |
| circuitBreaker_closedState | 0.342 μs | - |
| diagnostics_retrieval | 0.190 μs | - |
| healthCheck_overhead | 1.439 μs | - |
| tiered_caffeine_caffeine_slidingWindow | 0.335 μs | - |
| tiered_caffeine_caffeine_tokenBucket | 0.359 μs | - |
| tiered_concurrent_differentKeys | 2.881 μs | - |
| tiered_concurrent_sameKey | 3.295 μs | - |
| tiered_inmemory_inmemory_slidingWindow | 0.045 μs | - |
| tiered_inmemory_inmemory_tokenBucket | 0.039 μs | - |
| tiered_l1Failure_l2Fallback | 1.748 μs | - |
| tiered_randomKeys | 0.356 μs | - |

### SpEL Expression Performance

Comparison of SpEL compilation modes (validates 40× performance claim):

| Mode | Benchmark | Avg Latency | Throughput |
|------|-----------|-------------|------------|
| Other | cacheHit_sameExpression | - | 0.007 ops/ns |
| Other | cacheMiss_differentExpressions | - | 5.360 ops/us |
| Compiled | compiledSpEL_headerAccess | - | 7.496 ops/us |
| Compiled | compiledSpEL_simpleVariable | - | 14.871 ops/us |
| Compiled | compiledSpEL_userPlusIp | - | 7.914 ops/us |
| Other | complexSpEL_multiVariable | - | 4.436 ops/us |
| Interpreted | interpretedSpEL_cachedExpression | - | 5.931 ops/us |
| Interpreted | interpretedSpEL_parseEveryTime | - | 1.685 ops/us |
| Static | staticKey_noSpEL | - | 0.015 ops/ns |
| Other | cacheHit_sameExpression | 0.126 μs | - |
| Other | cacheMiss_differentExpressions | 0.187 μs | - |
| Compiled | compiledSpEL_headerAccess | 0.135 μs | - |
| Compiled | compiledSpEL_simpleVariable | 0.063 μs | - |
| Compiled | compiledSpEL_userPlusIp | 0.133 μs | - |
| Other | complexSpEL_multiVariable | 0.229 μs | - |
| Interpreted | interpretedSpEL_cachedExpression | 0.169 μs | - |
| Interpreted | interpretedSpEL_parseEveryTime | 0.577 μs | - |
| Static | staticKey_noSpEL | 0.068 μs | - |

### Algorithm Performance

Pure algorithm performance without storage overhead:

| Algorithm | Benchmark | Avg Latency | Throughput |
|-----------|-----------|-------------|------------|
| Token Bucket | burstScenario | - | 0.019 ops/ns |
| Token Bucket | concurrentKeys_tryAcquire | - | 1.943 ops/us |
| Token Bucket | pureAlgorithm_tryConsume | - | 0.210 ops/ns |
| Token Bucket | burstScenario | 0.053 μs | - |
| Token Bucket | concurrentKeys_tryAcquire | 1.930 μs | - |
| Token Bucket | pureAlgorithm_tryConsume | 0.005 μs | - |

## Running Benchmarks

To run benchmarks yourself:

```bash
# Build the benchmark jar
mvn clean package -pl rl-benchmarks -am -DskipTests

# Run all benchmarks (without Redis)
java -jar rl-benchmarks/target/benchmarks.jar

# Run with Redis
docker run -d -p 6379:6379 redis:7-alpine
java -Dredis.host=localhost -Dredis.port=6379 \
     -jar rl-benchmarks/target/benchmarks.jar

# Run specific benchmark
java -jar rl-benchmarks/target/benchmarks.jar StorageBenchmark
java -jar rl-benchmarks/target/benchmarks.jar TieredStorageBenchmark
java -jar rl-benchmarks/target/benchmarks.jar RedisBenchmark
java -jar rl-benchmarks/target/benchmarks.jar SpELBenchmark
java -jar rl-benchmarks/target/benchmarks.jar TokenBucketBenchmark
```

## Notes

### Understanding the Results

- **Throughput** is measured in operations per time unit (higher is better)
- **Latency** is measured in time per operation (lower is better)
- Results may vary based on hardware, JVM version, and system load

### Performance Expectations

| Storage Type | Expected Latency | Notes |
|--------------|------------------|-------|
| InMemory | < 50 ns | Fastest, no synchronization overhead |
| Caffeine | < 500 ns | Thread-safe with minimal overhead |
| Tiered (local) | < 1 μs | Small overhead for L1/L2 abstraction |
| Redis | ~500 μs | Network round-trip dominates |
| Tiered (Redis+Caffeine) | ~500 μs | Redis latency + minimal local overhead |

### Important Considerations

- Redis benchmarks require a running Redis instance
- Tiered storage uses Redis as L1 (distributed) and Caffeine as L2 (local fallback)
- Concurrent benchmarks use 8 threads to simulate real-world contention
- Health check and diagnostics for tiered Redis+Caffeine query both layers
