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

//*    Concept:
//*    Imagine a bucket that slowly fills with tokens at a fixed rate.
//*    To make a request, you need a token. If no token, request is denied.
//*
//*    How it works:
//*    Tokens are added at a constant rate until the bucket is full.
//*    A request takes one token immediately.
//*    Can handle bursts if enough tokens are saved.
//*
//*    Use case: Allows bursts but keeps the average rate under control.
//*
//*    Pros:
//*    Supports burst traffic.
//*    Easy to tune for average + peak rate.
//*
//*    Cons: Slightly more complex than Leaky Bucket.
//*
//*    Example in real life: API services where small bursts are allowed but not sustained flooding.

public class TokenBucketFilter implements Filter {

//    TODO: Implement max tokens limit in future and introduce multi-threading issues

    private final Map<String, TokenBucket> tokenBucketPerIpAddress = new ConcurrentHashMap<>();

    private static final int REFILL_TOKEN_PER_SECOND = 1;

    private static final int MAX_TOKENS_ALLOWED = 50;

    private static final Logger logger = LoggerFactory.getLogger(TokenBucketFilter.class);

    private static class TokenBucket  {
        int tokens;
        long lastRefillTimestamp;

        TokenBucket() {
            this.tokens = REFILL_TOKEN_PER_SECOND;
            this.lastRefillTimestamp = Instant.now().getEpochSecond();
        }

        private void useToken() {
            this.tokens = this.tokens - 1;
        }

        private void refillTokens() {
            long currentTimestamp = Instant.now().getEpochSecond();
            long windowsPassed = currentTimestamp - this.lastRefillTimestamp;
            if (windowsPassed > 0) {
                int tokensToAdd = (int) (windowsPassed * REFILL_TOKEN_PER_SECOND);
                this.tokens = Math.min(this.tokens + tokensToAdd, MAX_TOKENS_ALLOWED);
                this.lastRefillTimestamp = currentTimestamp;
            }
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        String clientIpAddress = httpServletRequest.getRemoteAddr();
        tokenBucketPerIpAddress.putIfAbsent(clientIpAddress, new TokenBucket());

        TokenBucket tokenBucket = tokenBucketPerIpAddress.get(clientIpAddress);
        tokenBucket.refillTokens();
        logger.info("Client IP: " + clientIpAddress + ", Tokens available: " + tokenBucket.tokens + ", Last Refill Timestamp: " + tokenBucket.lastRefillTimestamp);
        if(tokenBucket.tokens > 0) {
            tokenBucket.useToken();
            chain.doFilter(request, response);
        } else {
            httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpServletResponse.getWriter().write("Too many requests - Rate limit exceeded");
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
