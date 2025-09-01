package com.example.rate_limiter.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Leaky Bucket Filter (queue + steady outflow) - production-ready style
 *
 * Behavior:
 *  - Each client key has a bucket (bounded queue). If bucket is full -> reject (HTTP 429).
 *  - Incoming requests are converted to AsyncContext and enqueued (non-blocking).
 *  - A scheduled "leaker" per bucket (or global scheduler driving all buckets) drains requests
 *    at a steady rate and calls AsyncContext.start(...) to resume processing (chain.doFilter).
 *
 * Notes:
 *  - You must register the filter with asyncSupported = true.
 *  - Tune workerPool size, scheduler, bucket capacity, leak rate for your environment.
 */

//*    Concept:
//*    Hybrid between fixed window and sliding log.
//*    Uses counters for the current and previous window, then does a weighted calculation.
//*
//*    How it works:
//*    Track counts for two consecutive windows.
//*    Estimate rate by combining both with a proportion.
//*
//*    Use case: More accurate than fixed window, less memory than sliding log.
//*
//*    Pros:
//*    Memory efficient.
//*    Smoother rate limiting.
//*
//*    Cons: More complex logic.
//*
//*    Example in real life: APIs where exact fairness matters but memory constraints exist.

public class LeakyBucketFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketFilter.class);

    // global defaults (make configurable)
    private final int bucketCapacity;
    private final int leakRatePerSecond; // how many requests to process per second
    private final long asyncTimeoutMillis;
    private final ConcurrentMap<String, LeakyBucket> buckets = new ConcurrentHashMap<>();

    // scheduler to run leakers. We create one scheduler for all buckets to control thread count.
    private final ScheduledExecutorService scheduler;
    // optional pool to execute user requests (invoked inside AsyncContext.start or worker)
    private final ExecutorService executorService;

    public LeakyBucketFilter(int bucketCapacity, int leakRatePerSecond, long asyncTimeoutMillis) {
        this.bucketCapacity = bucketCapacity;
        this.leakRatePerSecond = leakRatePerSecond;
        this.asyncTimeoutMillis = asyncTimeoutMillis;

        // Single scheduler thread is enough because each run we may drain many buckets; tune if needed.
        this.scheduler = Executors.newScheduledThreadPool(1, runnable -> {
            Thread t = new Thread(runnable, "leaky-bucket-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Worker pool for executing request work after leak; runs chain.doFilter in container threads
        this.executorService = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors()),
                runnable -> {
                    Thread t = new Thread(runnable, "leaky-bucket-worker");
                    t.setDaemon(true);
                    return t;
                });

        // schedule periodic drain: run at fine granularity (e.g., every 200ms) and drain
        // proportional number of tokens per tick to reach leakRatePerSecond overall.
        long tickMillis = 200L;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                drainAllBuckets(tickMillis);
            } catch (Throwable t) {
                log.error("Error while draining buckets", t);
            }
        }, 0, tickMillis, TimeUnit.MILLISECONDS);
    }

    // The core filter method
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse resp)) {
            chain.doFilter(request, response);
            return;
        }
        // resolve client key (IP address here; consider X-Forwarded-For if behind proxies)
        String clientKey = resolveClientKey(req);

        // get/create bucket for this client
        LeakyBucket bucket = buckets.computeIfAbsent(clientKey,
                k -> new LeakyBucket(bucketCapacity, leakRatePerSecond));

        // Start async so we can return this thread to container; the request processing will
        // resume when the bucket leaks this AsyncContext.
        final AsyncContext async = req.startAsync();
        async.setTimeout(asyncTimeoutMillis);

        // Create a task that will execute the filter chain when dequeued
        RequestTask task = new RequestTask(async, chain);

        boolean accepted = bucket.tryEnqueue(task);
        if (!accepted) {
            // bucket full => immediate rejection
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);            resp.setHeader("Retry-After", "1"); // hint — tune as needed
            resp.getWriter().write("Too many requests - rate limit exceeded");
            async.complete(); // ensure async is completed
            return;
        }

        // otherwise: request is enqueued and will be processed later by the bucket leaker.
        // Return immediately — AsyncContext will keep request alive until dispatched/complete.
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }

    /**
     * Task wrapping the AsyncContext and FilterChain to call chain.doFilter when executed.
     * We store the AsyncContext (which holds request/response), and when run we call
     * chain.doFilter(ctx.getRequest(), ctx.getResponse()) inside AsyncContext.start to
     * get a container-managed thread.
     */
    private static final class RequestTask implements Runnable {
        final AsyncContext asyncContext;
        final FilterChain chain;

        RequestTask(AsyncContext asyncContext, FilterChain chain) {
            this.asyncContext = Objects.requireNonNull(asyncContext);
            this.chain = Objects.requireNonNull(chain);
        }

        @Override
        public void run() {
            try {
                // Start processing inside container managed thread pool for async
                asyncContext.start(() -> {
                    try {
                        ServletRequest req = asyncContext.getRequest();
                        ServletResponse resp = asyncContext.getResponse();
                        // Run the filter chain (your application servlet will handle the request)
                        chain.doFilter(req, resp);
                    } catch (Throwable ex) {
                        log.error("Error while processing request from bucket", ex);
                        try {
                            ((HttpServletResponse) asyncContext.getResponse()).setStatus(500);
                        } catch (Exception ignored) {}
                    } finally {
                        try {
                            asyncContext.complete();
                        } catch (Exception ignored) {}
                    }
                });
            } catch (Throwable ex) {
                log.error("Failed to dispatch async request", ex);
                try { asyncContext.complete(); } catch (Exception ignored) {}
            }
        }
    }

    // Shutdown helper
    public void shutdown() {
        try {
            scheduler.shutdown();
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        try {
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    // Resolve client IP — be careful when behind proxies; prefer X-Forwarded-For handling in prod
    private String  resolveClientKey(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    // Drain all buckets for the given tick duration; compute tokens per tick from leakRatePerSecond.
    private void drainAllBuckets(long tickMillis) {
        // tokens to process per tick (rounding down); ensures overall approx leakRatePerSecond
        double ticksPerSecond = 1000.0 / tickMillis;
        int tokensPerTick = Math.max(1, (int) Math.round(leakRatePerSecond / ticksPerSecond));

        for (LeakyBucket bucket : buckets.values()) {
            bucket.drain(tokensPerTick);
        }
    }

    private static final class LeakyBucket {
        private final BlockingQueue<RequestTask> queue;
        private final int capacity;
        private final AtomicInteger queued = new AtomicInteger(0);

        LeakyBucket(int capacity, int leakRatePerSecond) {
            this.capacity = capacity;
            this.queue = new ArrayBlockingQueue<>(capacity);
        }

        // try to enqueue without blocking
        boolean tryEnqueue(RequestTask task) {
            boolean offered = queue.offer(task);
            if (offered) queued.incrementAndGet();
            return offered;
        }

        // drain up to 'count' tasks from queue and submit them for processing
        void drain(int count) {
            for (int i = 0; i < count; i++) {
                RequestTask t = queue.poll();
                if (t == null) break;
                queued.decrementAndGet();
                try {
                    // Execute the task; it will call chain.doFilter inside async context
                    t.run();
                } catch (Throwable ex) {
                    log.error("Error executing leaked task", ex);
                    try {
                        t.asyncContext.complete();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        int size() { return queued.get(); }
    }
}