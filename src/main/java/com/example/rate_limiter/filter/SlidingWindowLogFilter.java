package com.example.rate_limiter.filter;

/*
Concept: Keep timestamps of each request and check the last X seconds dynamically.

How it works:
Maintain a log (list) of request timestamps.
Remove timestamps older than the time window.
If count exceeds limit, deny request.

Use case: Accurate per-user tracking over a moving time window.

Pros: Fairer than fixed window.

Cons: High memory usage for many requests (stores all timestamps).

Example in real life: Chat systems preventing more than N messages in a rolling 10-second window.
*/

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class SlidingWindowLogFilter implements Filter {

    private final Map<String, List<Long>> requestTimestampsPerIpAddress = new ConcurrentHashMap<>();

    private static final int MAX_REQUESTS_PER_MINUTE = 5;

    private static final Logger logger = LoggerFactory.getLogger(SlidingWindowLogFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        String clientIpAddress = httpServletRequest.getRemoteAddr();
        Long currentInstant = Instant.now().getEpochSecond();
        logger.info(currentInstant.toString());

        if(requestTimestampsPerIpAddress.get(clientIpAddress) != null){
            removeExpiredTimestamps(requestTimestampsPerIpAddress.get(clientIpAddress));
        }

        if(requestTimestampsPerIpAddress.get(clientIpAddress) == null){
            requestTimestampsPerIpAddress.putIfAbsent(clientIpAddress, new ArrayList<>(List.of(currentInstant)));
        } else if(requestTimestampsPerIpAddress.get(clientIpAddress).size() < MAX_REQUESTS_PER_MINUTE+1){
            requestTimestampsPerIpAddress.get(clientIpAddress).add(currentInstant);
        }
        int requestCount = requestTimestampsPerIpAddress.get(clientIpAddress).size();

        logger.info(requestTimestampsPerIpAddress.toString());
        // Check if the request limit has been exceeded
        if (requestCount >= MAX_REQUESTS_PER_MINUTE+1) {
            httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpServletResponse.getWriter().write("Too many requests. Please try again later.");
            return;
        }

        chain.doFilter(request, response);
    }

    private void removeExpiredTimestamps(List<Long> timeStamps) {
        long oneMinuteAgo = Instant.now().getEpochSecond() - 60;
        timeStamps.removeIf(timestamp -> timestamp < oneMinuteAgo);
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
