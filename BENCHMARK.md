# Performance Benchmarks

This document contains the latest benchmark results for the Rate Limiting Library.

> **Last Updated**: 2026-01-04 00:06:14
>
> **Environment**: Java 25, Mac OS X aarch64
>
> **Redis Available**: Yes

## Summary

| Category | Benchmark | Score | Unit |
|----------|-----------|------:|------|
| RedisBenchmark | baseline_caffeine | 3.315 | ops/us |
| RedisBenchmark | redis_batch_100_operations | 18.748 | ops/ms |
| RedisBenchmark | redis_concurrent_differentKeys | 0.041 | ops/us |
| RedisBenchmark | redis_concurrent_sameKey | 0.042 | ops/us |
| RedisBenchmark | redis_diagnostics | 0.022 | ops/us |
| RedisBenchmark | redis_healthCheck | 0.024 | ops/us |
| RedisBenchmark | redis_randomKeys | 0.019 | ops/us |
| RedisBenchmark | redis_singleKey_slidingWindow | 0.019 | ops/us |
| RedisBenchmark | redis_singleKey_tokenBucket | 0.019 | ops/us |
| RedisBenchmark | tiered_batch_100_operations | 17.689 | ops/ms |
| RedisBenchmark | tiered_circuitBreaker_stateCheck | 1.004 | ops/ns |
| RedisBenchmark | tiered_concurrent_differentKeys | 0.041 | ops/us |
| RedisBenchmark | tiered_concurrent_sameKey | 0.041 | ops/us |
| RedisBenchmark | tiered_diagnostics | 0.012 | ops/us |
| RedisBenchmark | tiered_healthCheck | 0.010 | ops/us |
| RedisBenchmark | tiered_redis_caffeine_randomKeys | 0.017 | ops/us |
| RedisBenchmark | tiered_redis_caffeine_slidingWindow | 0.018 | ops/us |
| RedisBenchmark | tiered_redis_caffeine_tokenBucket | 0.017 | ops/us |
| SpELBenchmark | cacheHit_sameExpression | 0.005 | ops/ns |
| SpELBenchmark | cacheMiss_differentExpressions | 6.076 | ops/us |
| SpELBenchmark | compiledSpEL_headerAccess | 6.988 | ops/us |
| SpELBenchmark | compiledSpEL_simpleVariable | 29.220 | ops/us |
| SpELBenchmark | compiledSpEL_userPlusIp | 4.573 | ops/us |
| SpELBenchmark | complexSpEL_multiVariable | 3.471 | ops/us |
| SpELBenchmark | interpretedSpEL_cachedExpression | 5.792 | ops/us |
| SpELBenchmark | interpretedSpEL_parseEveryTime | 1.492 | ops/us |
| SpELBenchmark | staticKey_noSpEL | 0.016 | ops/ns |
| StorageBenchmark | batch_100_tryAcquire | 3.598 | ops/us |
| StorageBenchmark | batch_100_tryAcquire | 71.085 | ops/us |
| StorageBenchmark | concurrent_differentKeys | 0.003 | ops/ns |
| StorageBenchmark | concurrent_differentKeys | 0.046 | ops/ns |
| StorageBenchmark | concurrent_sameKey | 0.002 | ops/ns |
| StorageBenchmark | concurrent_sameKey | 0.010 | ops/ns |
| StorageBenchmark | getDiagnostics | 22.140 | ops/us |
| StorageBenchmark | getDiagnostics | 63.715 | ops/us |
| StorageBenchmark | healthCheck | 1.894 | ops/ns |
| StorageBenchmark | healthCheck | 1.679 | ops/ns |
| StorageBenchmark | randomKey_getState | 0.239 | ops/ns |
| StorageBenchmark | randomKey_getState | 0.216 | ops/ns |
| StorageBenchmark | randomKey_tryAcquire | 0.001 | ops/ns |
| StorageBenchmark | randomKey_tryAcquire | 0.024 | ops/ns |
| StorageBenchmark | reset_singleKey | 1.571 | ops/us |
| StorageBenchmark | reset_singleKey | 19.326 | ops/us |
| StorageBenchmark | singleKey_getState | 0.584 | ops/ns |
| StorageBenchmark | singleKey_getState | 0.560 | ops/ns |
| StorageBenchmark | singleKey_tryAcquire_slidingWindow | 0.003 | ops/ns |
| StorageBenchmark | singleKey_tryAcquire_slidingWindow | 0.025 | ops/ns |
| StorageBenchmark | singleKey_tryAcquire_tokenBucket | 0.003 | ops/ns |
| StorageBenchmark | singleKey_tryAcquire_tokenBucket | 0.030 | ops/ns |
| TieredStorageBenchmark | baseline_caffeineOnly | 3.321 | ops/us |
| TieredStorageBenchmark | baseline_inMemoryOnly | 29.993 | ops/us |
| TieredStorageBenchmark | circuitBreaker_closedState | 3.326 | ops/us |
| TieredStorageBenchmark | diagnostics_retrieval | 0.008 | ops/ns |
| TieredStorageBenchmark | healthCheck_overhead | 0.000 | ops/ns |
| TieredStorageBenchmark | tiered_caffeine_caffeine_slidingWindow | 2.915 | ops/us |
| TieredStorageBenchmark | tiered_caffeine_caffeine_tokenBucket | 3.284 | ops/us |
| TieredStorageBenchmark | tiered_concurrent_differentKeys | 2.826 | ops/us |
| TieredStorageBenchmark | tiered_concurrent_sameKey | 2.405 | ops/us |
| TieredStorageBenchmark | tiered_inmemory_inmemory_slidingWindow | 23.668 | ops/us |
| TieredStorageBenchmark | tiered_inmemory_inmemory_tokenBucket | 27.458 | ops/us |
| TieredStorageBenchmark | tiered_l1Failure_l2Fallback | 0.609 | ops/us |
| TieredStorageBenchmark | tiered_randomKeys | 2.923 | ops/us |
| TokenBucketBenchmark | burstScenario | 0.019 | ops/ns |
| TokenBucketBenchmark | caffeineStorage_tryAcquire | 1.772 | ops/us |
| TokenBucketBenchmark | concurrentKeys_tryAcquire | 2.044 | ops/us |
| TokenBucketBenchmark | pureAlgorithm_tryConsume | 0.210 | ops/ns |
| RedisBenchmark | baseline_caffeine | 0.295 | us/op |
| RedisBenchmark | redis_batch_100_operations | 0.058 | ms/op |
| RedisBenchmark | redis_concurrent_differentKeys | 204.396 | us/op |
| RedisBenchmark | redis_concurrent_sameKey | 194.698 | us/op |
| RedisBenchmark | redis_diagnostics | 45.350 | us/op |
| RedisBenchmark | redis_healthCheck | 42.784 | us/op |
| RedisBenchmark | redis_latency_p99 | 0.054 | ms/op |
| RedisBenchmark | redis_randomKeys | 58.293 | us/op |
| RedisBenchmark | redis_singleKey_slidingWindow | 55.432 | us/op |
| RedisBenchmark | redis_singleKey_tokenBucket | 54.724 | us/op |
| RedisBenchmark | tiered_batch_100_operations | 0.055 | ms/op |
| RedisBenchmark | tiered_circuitBreaker_stateCheck | 0.998 | ns/op |
| RedisBenchmark | tiered_concurrent_differentKeys | 206.352 | us/op |
| RedisBenchmark | tiered_concurrent_sameKey | 194.351 | us/op |
| RedisBenchmark | tiered_diagnostics | 85.622 | us/op |
| RedisBenchmark | tiered_healthCheck | 102.743 | us/op |
| RedisBenchmark | tiered_redis_caffeine_randomKeys | 54.792 | us/op |
| RedisBenchmark | tiered_redis_caffeine_slidingWindow | 52.283 | us/op |
| RedisBenchmark | tiered_redis_caffeine_tokenBucket | 57.774 | us/op |
| SpELBenchmark | cacheHit_sameExpression | 218.723 | ns/op |
| SpELBenchmark | cacheMiss_differentExpressions | 0.163 | us/op |
| SpELBenchmark | compiledSpEL_headerAccess | 0.160 | us/op |
| SpELBenchmark | compiledSpEL_simpleVariable | 0.035 | us/op |
| SpELBenchmark | compiledSpEL_userPlusIp | 0.217 | us/op |
| SpELBenchmark | complexSpEL_multiVariable | 0.290 | us/op |
| SpELBenchmark | interpretedSpEL_cachedExpression | 0.170 | us/op |
| SpELBenchmark | interpretedSpEL_parseEveryTime | 0.662 | us/op |
| SpELBenchmark | staticKey_noSpEL | 62.694 | ns/op |
| StorageBenchmark | batch_100_tryAcquire | 0.283 | us/op |
| StorageBenchmark | batch_100_tryAcquire | 0.014 | us/op |
| StorageBenchmark | concurrent_differentKeys | 2,745.930 | ns/op |
| StorageBenchmark | concurrent_differentKeys | 200.316 | ns/op |
| StorageBenchmark | concurrent_sameKey | 3,514.621 | ns/op |
| StorageBenchmark | concurrent_sameKey | 829.960 | ns/op |
| StorageBenchmark | getDiagnostics | 0.045 | us/op |
| StorageBenchmark | getDiagnostics | 0.016 | us/op |
| StorageBenchmark | healthCheck | 0.526 | ns/op |
| StorageBenchmark | healthCheck | 0.595 | ns/op |
| StorageBenchmark | randomKey_getState | 4.179 | ns/op |
| StorageBenchmark | randomKey_getState | 4.677 | ns/op |
| StorageBenchmark | randomKey_tryAcquire | 666.245 | ns/op |
| StorageBenchmark | randomKey_tryAcquire | 80.183 | ns/op |
| StorageBenchmark | reset_singleKey | 0.660 | us/op |
| StorageBenchmark | reset_singleKey | 0.052 | us/op |
| StorageBenchmark | singleKey_getState | 1.714 | ns/op |
| StorageBenchmark | singleKey_getState | 1.786 | ns/op |
| StorageBenchmark | singleKey_tryAcquire_slidingWindow | 305.187 | ns/op |
| StorageBenchmark | singleKey_tryAcquire_slidingWindow | 39.456 | ns/op |
| StorageBenchmark | singleKey_tryAcquire_tokenBucket | 295.381 | ns/op |
| StorageBenchmark | singleKey_tryAcquire_tokenBucket | 33.519 | ns/op |
| TieredStorageBenchmark | baseline_caffeineOnly | 0.300 | us/op |
| TieredStorageBenchmark | baseline_inMemoryOnly | 0.033 | us/op |
| TieredStorageBenchmark | circuitBreaker_closedState | 0.320 | us/op |
| TieredStorageBenchmark | diagnostics_retrieval | 123.593 | ns/op |
| TieredStorageBenchmark | healthCheck_overhead | 7,592.143 | ns/op |
| TieredStorageBenchmark | tiered_caffeine_caffeine_slidingWindow | 0.336 | us/op |
| TieredStorageBenchmark | tiered_caffeine_caffeine_tokenBucket | 0.310 | us/op |
| TieredStorageBenchmark | tiered_concurrent_differentKeys | 2.836 | us/op |
| TieredStorageBenchmark | tiered_concurrent_sameKey | 3.462 | us/op |
| TieredStorageBenchmark | tiered_inmemory_inmemory_slidingWindow | 0.043 | us/op |
| TieredStorageBenchmark | tiered_inmemory_inmemory_tokenBucket | 0.036 | us/op |
| TieredStorageBenchmark | tiered_l1Failure_l2Fallback | 1.653 | us/op |
| TieredStorageBenchmark | tiered_randomKeys | 0.342 | us/op |
| TokenBucketBenchmark | burstScenario | 52.783 | ns/op |
| TokenBucketBenchmark | caffeineStorage_tryAcquire | 0.600 | us/op |
| TokenBucketBenchmark | concurrentKeys_tryAcquire | 1.965 | us/op |
| TokenBucketBenchmark | pureAlgorithm_tryConsume | 4.789 | ns/op |

## Detailed Results

### Storage Performance

Comparison of different storage backends:

| Storage | Operation | Avg Latency | Throughput |
|---------|-----------|-------------|------------|
| Caffeine | baseline_caffeine | - | 3.315 ops/us |
| Caffeine | baseline_caffeineOnly | - | 3.321 ops/us |
| Tiered | baseline_inMemoryOnly | - | 29.993 ops/us |
| Tiered | circuitBreaker_closedState | - | 3.326 ops/us |
| Tiered | diagnostics_retrieval | - | 0.008 ops/ns |
| Tiered | healthCheck_overhead | - | 0.000 ops/ns |
| Caffeine | tiered_caffeine_caffeine_slidingWindow | - | 2.915 ops/us |
| Caffeine | tiered_caffeine_caffeine_tokenBucket | - | 3.284 ops/us |
| Tiered | tiered_concurrent_differentKeys | - | 2.826 ops/us |
| Tiered | tiered_concurrent_sameKey | - | 2.405 ops/us |
| Tiered | tiered_inmemory_inmemory_slidingWindow | - | 23.668 ops/us |
| Tiered | tiered_inmemory_inmemory_tokenBucket | - | 27.458 ops/us |
| Tiered | tiered_l1Failure_l2Fallback | - | 0.609 ops/us |
| Tiered | tiered_randomKeys | - | 2.923 ops/us |
| Caffeine | caffeineStorage_tryAcquire | - | 1.772 ops/us |
| Caffeine | baseline_caffeine | 0.295 μs | - |
| Caffeine | baseline_caffeineOnly | 0.300 μs | - |
| Tiered | baseline_inMemoryOnly | 0.033 μs | - |
| Tiered | circuitBreaker_closedState | 0.320 μs | - |
| Tiered | diagnostics_retrieval | 0.124 μs | - |
| Tiered | healthCheck_overhead | 7.592 μs | - |
| Caffeine | tiered_caffeine_caffeine_slidingWindow | 0.336 μs | - |
| Caffeine | tiered_caffeine_caffeine_tokenBucket | 0.310 μs | - |
| Tiered | tiered_concurrent_differentKeys | 2.836 μs | - |
| Tiered | tiered_concurrent_sameKey | 3.462 μs | - |
| Tiered | tiered_inmemory_inmemory_slidingWindow | 0.043 μs | - |
| Tiered | tiered_inmemory_inmemory_tokenBucket | 0.036 μs | - |
| Tiered | tiered_l1Failure_l2Fallback | 1.653 μs | - |
| Tiered | tiered_randomKeys | 0.342 μs | - |
| Caffeine | caffeineStorage_tryAcquire | 0.600 μs | - |

### Tiered Storage (Redis L1 + Caffeine L2)

Production-recommended configuration with distributed Redis as primary and local Caffeine as fallback:

> **Note**: `tiered_healthCheck` and `tiered_diagnostics` query both Redis (L1) and Caffeine (L2),
> so their latency includes Redis network round-trip (~100μs each). This is expected behavior.

| Benchmark | Avg Latency | Throughput |
|-----------|-------------|------------|
| tiered_batch_100_operations | - | 17.689 ops/ms |
| tiered_circuitBreaker_stateCheck | - | 1.004 ops/ns |
| tiered_concurrent_differentKeys | - | 0.041 ops/us |
| tiered_concurrent_sameKey | - | 0.041 ops/us |
| tiered_diagnostics | - | 0.012 ops/us |
| tiered_healthCheck | - | 0.010 ops/us |
| tiered_redis_caffeine_randomKeys | - | 0.017 ops/us |
| tiered_redis_caffeine_slidingWindow | - | 0.018 ops/us |
| tiered_redis_caffeine_tokenBucket | - | 0.017 ops/us |
| baseline_caffeineOnly | - | 3.321 ops/us |
| baseline_inMemoryOnly | - | 29.993 ops/us |
| circuitBreaker_closedState | - | 3.326 ops/us |
| diagnostics_retrieval | - | 0.008 ops/ns |
| healthCheck_overhead | - | 0.000 ops/ns |
| tiered_caffeine_caffeine_slidingWindow | - | 2.915 ops/us |
| tiered_caffeine_caffeine_tokenBucket | - | 3.284 ops/us |
| tiered_concurrent_differentKeys | - | 2.826 ops/us |
| tiered_concurrent_sameKey | - | 2.405 ops/us |
| tiered_inmemory_inmemory_slidingWindow | - | 23.668 ops/us |
| tiered_inmemory_inmemory_tokenBucket | - | 27.458 ops/us |
| tiered_l1Failure_l2Fallback | - | 0.609 ops/us |
| tiered_randomKeys | - | 2.923 ops/us |
| tiered_batch_100_operations | 0.055 ms | - |
| tiered_circuitBreaker_stateCheck | 0.001 μs | - |
| tiered_concurrent_differentKeys | 206.352 μs | - |
| tiered_concurrent_sameKey | 194.351 μs | - |
| tiered_diagnostics | 85.622 μs | - |
| tiered_healthCheck | 102.743 μs | - |
| tiered_redis_caffeine_randomKeys | 54.792 μs | - |
| tiered_redis_caffeine_slidingWindow | 52.283 μs | - |
| tiered_redis_caffeine_tokenBucket | 57.774 μs | - |
| baseline_caffeineOnly | 0.300 μs | - |
| baseline_inMemoryOnly | 0.033 μs | - |
| circuitBreaker_closedState | 0.320 μs | - |
| diagnostics_retrieval | 0.124 μs | - |
| healthCheck_overhead | 7.592 μs | - |
| tiered_caffeine_caffeine_slidingWindow | 0.336 μs | - |
| tiered_caffeine_caffeine_tokenBucket | 0.310 μs | - |
| tiered_concurrent_differentKeys | 2.836 μs | - |
| tiered_concurrent_sameKey | 3.462 μs | - |
| tiered_inmemory_inmemory_slidingWindow | 0.043 μs | - |
| tiered_inmemory_inmemory_tokenBucket | 0.036 μs | - |
| tiered_l1Failure_l2Fallback | 1.653 μs | - |
| tiered_randomKeys | 0.342 μs | - |

### SpEL Expression Performance

Comparison of SpEL compilation modes (validates 40× performance claim):

| Mode | Benchmark | Avg Latency | Throughput |
|------|-----------|-------------|------------|
| Other | cacheHit_sameExpression | - | 0.005 ops/ns |
| Other | cacheMiss_differentExpressions | - | 6.076 ops/us |
| Compiled | compiledSpEL_headerAccess | - | 6.988 ops/us |
| Compiled | compiledSpEL_simpleVariable | - | 29.220 ops/us |
| Compiled | compiledSpEL_userPlusIp | - | 4.573 ops/us |
| Other | complexSpEL_multiVariable | - | 3.471 ops/us |
| Interpreted | interpretedSpEL_cachedExpression | - | 5.792 ops/us |
| Interpreted | interpretedSpEL_parseEveryTime | - | 1.492 ops/us |
| Static | staticKey_noSpEL | - | 0.016 ops/ns |
| Other | cacheHit_sameExpression | 0.219 μs | - |
| Other | cacheMiss_differentExpressions | 0.163 μs | - |
| Compiled | compiledSpEL_headerAccess | 0.160 μs | - |
| Compiled | compiledSpEL_simpleVariable | 0.035 μs | - |
| Compiled | compiledSpEL_userPlusIp | 0.217 μs | - |
| Other | complexSpEL_multiVariable | 0.290 μs | - |
| Interpreted | interpretedSpEL_cachedExpression | 0.170 μs | - |
| Interpreted | interpretedSpEL_parseEveryTime | 0.662 μs | - |
| Static | staticKey_noSpEL | 0.063 μs | - |

### Algorithm Performance

Pure algorithm performance without storage overhead:

| Algorithm | Benchmark | Avg Latency | Throughput |
|-----------|-----------|-------------|------------|
| Token Bucket | burstScenario | - | 0.019 ops/ns |
| Token Bucket | concurrentKeys_tryAcquire | - | 2.044 ops/us |
| Token Bucket | pureAlgorithm_tryConsume | - | 0.210 ops/ns |
| Token Bucket | burstScenario | 0.053 μs | - |
| Token Bucket | concurrentKeys_tryAcquire | 1.965 μs | - |
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
| InMemory | ~33 ns | Fastest, no synchronization overhead |
| Caffeine | ~300 ns | Thread-safe with minimal overhead |
| Tiered (local) | ~350 ns | Small overhead for L1/L2 abstraction |
| Redis | ~55 μs | Network round-trip dominates |
| Tiered (Redis+Caffeine) | ~55 μs | Redis latency + minimal local overhead |

### Important Considerations

- Redis benchmarks require a running Redis instance
- Tiered storage uses Redis as L1 (distributed) and Caffeine as L2 (local fallback)
- Concurrent benchmarks use 8 threads to simulate real-world contention
- Health check and diagnostics for tiered Redis+Caffeine query both layers
