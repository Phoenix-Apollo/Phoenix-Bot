package Botcode.Testing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Load testing framework for performance and stress testing.
 * Simulates multiple concurrent users executing commands.
 * Measures response times, throughput, and identifies bottlenecks.
 */
public class LoadTestRunner {

  public static class LoadTestResult {
    public final long totalRequests;
    public final long successfulRequests;
    public final long failedRequests;
    public final long totalTimeMs;
    public final double requestsPerSecond;
    public final double averageResponseTimeMs;
    public final double minResponseTimeMs;
    public final double maxResponseTimeMs;
    public final double p95ResponseTimeMs;
    public final double p99ResponseTimeMs;

    public LoadTestResult(
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        long totalTimeMs,
        double requestsPerSecond,
        double averageResponseTimeMs,
        double minResponseTimeMs,
        double maxResponseTimeMs,
        double p95ResponseTimeMs,
        double p99ResponseTimeMs) {
      this.totalRequests = totalRequests;
      this.successfulRequests = successfulRequests;
      this.failedRequests = failedRequests;
      this.totalTimeMs = totalTimeMs;
      this.requestsPerSecond = requestsPerSecond;
      this.averageResponseTimeMs = averageResponseTimeMs;
      this.minResponseTimeMs = minResponseTimeMs;
      this.maxResponseTimeMs = maxResponseTimeMs;
      this.p95ResponseTimeMs = p95ResponseTimeMs;
      this.p99ResponseTimeMs = p99ResponseTimeMs;
    }

    @Override
    public String toString() {
      return "=== LOAD TEST RESULTS ===\n" +
          "Total Requests: " + totalRequests + "\n" +
          "Successful: " + successfulRequests + " (" + String.format("%.2f%%",
          (successfulRequests * 100.0) / totalRequests) + ")\n" +
          "Failed: " + failedRequests + "\n" +
          "Total Time: " + totalTimeMs + "ms\n" +
          "Throughput: " + String.format("%.2f", requestsPerSecond) + " req/sec\n" +
          "Avg Response Time: " + String.format("%.2f", averageResponseTimeMs) + "ms\n" +
          "Min Response Time: " + String.format("%.2f", minResponseTimeMs) + "ms\n" +
          "Max Response Time: " + String.format("%.2f", maxResponseTimeMs) + "ms\n" +
          "P95 Response Time: " + String.format("%.2f", p95ResponseTimeMs) + "ms\n" +
          "P99 Response Time: " + String.format("%.2f", p99ResponseTimeMs) + "ms";
    }
  }

  private final java.util.List<Long> responseTimes = java.util.Collections
      .synchronizedList(new java.util.ArrayList<>());
  private final AtomicInteger successCount = new AtomicInteger(0);
  private final AtomicInteger failureCount = new AtomicInteger(0);
  private final AtomicLong totalResponseTime = new AtomicLong(0);

  /**
   * Runs a load test with specified parameters.
   *
   * @param concurrentUsers Number of concurrent simulated users
   * @param requestsPerUser Number of requests each user makes
   * @param testFunction Function to execute for each request (should return true if successful)
   * @return LoadTestResult with performance metrics
   */
  public LoadTestResult runLoadTest(
      int concurrentUsers,
      int requestsPerUser,
      java.util.function.Supplier<Boolean> testFunction) {

    System.out.println("[LoadTest] Starting test with " + concurrentUsers + " users, "
        + requestsPerUser + " requests each...");

    long startTime = System.currentTimeMillis();
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(concurrentUsers);

    for (int i = 0; i < concurrentUsers; i++) {
      new Thread(() -> {
        try {
          startLatch.await();

          for (int j = 0; j < requestsPerUser; j++) {
            long requestStart = System.currentTimeMillis();

            try {
              boolean success = testFunction.get();
              long requestTime = System.currentTimeMillis() - requestStart;

              responseTimes.add(requestTime);
              totalResponseTime.addAndGet(requestTime);

              if (success) {
                successCount.incrementAndGet();
              } else {
                failureCount.incrementAndGet();
              }
            } catch (Exception e) {
              failureCount.incrementAndGet();
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          endLatch.countDown();
        }
      }).start();
    }

    try {
      startLatch.countDown();
      endLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    long totalTimeMs = System.currentTimeMillis() - startTime;
    return calculateResults(concurrentUsers * requestsPerUser, totalTimeMs);
  }

  /**
   * Calculates test results from collected response times.
   *
   * @param totalRequests Total number of requests made
   * @param totalTimeMs Total test duration in milliseconds
   * @return LoadTestResult with calculated metrics
   */
  private LoadTestResult calculateResults(long totalRequests, long totalTimeMs) {
    long successful = successCount.get();
    long failed = failureCount.get();

    double requestsPerSecond = (totalRequests * 1000.0) / totalTimeMs;
    double averageResponseTime = totalResponseTime.get() > 0
        ? totalResponseTime.get() / (double) totalRequests
        : 0;

    java.util.List<Long> sortedTimes = new java.util.ArrayList<>(responseTimes);
    java.util.Collections.sort(sortedTimes);

    double minTime = sortedTimes.isEmpty() ? 0 : sortedTimes.get(0);
    double maxTime = sortedTimes.isEmpty() ? 0 : sortedTimes.get(sortedTimes.size() - 1);

    double p95 = calculatePercentile(sortedTimes, 95);
    double p99 = calculatePercentile(sortedTimes, 99);

    System.out.println("[LoadTest] Test completed in " + totalTimeMs + "ms");

    return new LoadTestResult(
        totalRequests,
        successful,
        failed,
        totalTimeMs,
        requestsPerSecond,
        averageResponseTime,
        minTime,
        maxTime,
        p95,
        p99);
  }

  /**
   * Calculates the Nth percentile from sorted response times.
   *
   * @param sortedTimes Sorted list of response times
   * @param percentile Percentile to calculate (0-100)
   * @return Response time at the given percentile
   */
  private double calculatePercentile(java.util.List<Long> sortedTimes, int percentile) {
    if (sortedTimes.isEmpty()) {
      return 0;
    }

    int index = (int) Math.ceil((percentile / 100.0) * sortedTimes.size()) - 1;
    index = Math.max(0, Math.min(index, sortedTimes.size() - 1));
    return sortedTimes.get(index);
  }
}
