package com.example.rate_limiter.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

//*    Concept:
//*    Hybrid between fixed window and sliding log.
//*    Uses counters for the current and previous window, then does a weighted calculation.
//*
//*    How it works: Track counts for two consecutive windows. Estimate rate by combining both with a proportion.
//*
//*    Use case: More accurate than fixed window, less memory than sliding log.
//*
//*    Pros: Memory efficient, Smoother rate limiting.
//*
//*    Cons: More complex logic.
//*
//*    Example in real life: APIs where exact fairness matters but memory constraints exist.

public class SlidingWindowCounterFilter implements Filter {

    private static class WindowData {
        long windowStartTime;
        int previousCount;
        int currentCount;

        WindowData() {
            this.windowStartTime = ( Instant.now().getEpochSecond() / 60 ) * 60;
            this.previousCount = 0;
            this.currentCount = 0;
        }
    }

    private final Map<String, WindowData> requestCountsPerIpAddress = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(FixedWindowCounterFilter.class);

    private static final int MAX_REQUESTS_PER_MINUTE = 5;

//    in seconds
    private static final int MAX_WINDOW_SIZE = 60;


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        String clientIpAddress = httpServletRequest.getRemoteAddr();
        requestCountsPerIpAddress.putIfAbsent(clientIpAddress, new WindowData());
        WindowData windowData = requestCountsPerIpAddress.get(clientIpAddress);

        long currentTime = Instant.now().getEpochSecond();
        long windowStartTime = (currentTime / MAX_WINDOW_SIZE) * MAX_WINDOW_SIZE;

        if (windowData.windowStartTime < windowStartTime) {
            windowData.previousCount = windowData.currentCount;
            windowData.currentCount = 0;
            windowData.windowStartTime = windowStartTime;
        }

        // increment for this request
        windowData.currentCount++;
        int currentWindowCounter = windowData.currentCount;
        int previousWindowCounter = windowData.previousCount;

        //*     weight = (remaining time in window / window size)
        double weight = (double) (MAX_WINDOW_SIZE - (currentTime - windowStartTime)) / MAX_WINDOW_SIZE;
        //*     currentCount + weight * previousCount
        logger.info("WindowStartTime: " + windowData.windowStartTime + ", currentWindowCounter: " + currentWindowCounter + ", previousWindowCounter: " + previousWindowCounter + ", weight: " + weight);
        double effectiveCount = currentWindowCounter + (weight * previousWindowCounter);

        if (effectiveCount > MAX_REQUESTS_PER_MINUTE) {
            httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpServletResponse.getWriter().write("Too many requests. Please try again later.");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void destroy() {}
}
