package com.example.rate_limiter.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


//*     Concept: Split time into fixed windows (e.g., each minute) and count requests per window.
//*
//*     How it works:
//*         Each window resets the counter to zero.
//*         If count exceeds the limit, requests are denied until the window resets.
//*
//*     Use case: Easy to implement for API calls per second/minute/hour.
//*
//*     Pros: Simple logic and memory usage.
//*
//*     Cons: Problem with boundary spikes (e.g., 10 requests at end of one window + 10 at start of next window → 20 in quick succession).
//*
//*     Example in real life: Web APIs like 100 requests per minute.


//@Component
public class FixedWindowCounterFilter implements Filter {

//   TODO: requestCountsPerIpAddress will keep on increasing and will give outOFbounds error if more requests, Handle this in future

    private final Map<String, AtomicInteger> requestCountsPerIpAddress = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(FixedWindowCounterFilter.class);

    // Maximum requests allowed per minute
    private static final int MAX_REQUESTS_PER_MINUTE = 5;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        String clientIpAddress = httpServletRequest.getRemoteAddr();
        Integer currentInstant = Math.toIntExact(Instant.now().getEpochSecond() / 60);
        String key = clientIpAddress + currentInstant.toString();
        // Initialize request count for the client IP address
        logger.info("key: " + key);
        requestCountsPerIpAddress.putIfAbsent(key, new AtomicInteger(0));
        AtomicInteger requestCount = requestCountsPerIpAddress.get(key);

        // Increment the request count
        int requests = requestCount.incrementAndGet();
        logger.info(requestCountsPerIpAddress.toString());
        // Check if the request limit has been exceeded
        if (requests > MAX_REQUESTS_PER_MINUTE) {
            httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpServletResponse.getWriter().write("Too many requests. Please try again later.");
            return;
        }

        // Allow the request to proceed
        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Optional: Initialization logic, if needed
    }

    @Override
    public void destroy() {
        // Optional: Cleanup resources, if needed
    }
}
