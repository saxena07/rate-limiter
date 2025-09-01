//package com.example.rate_limiter.filter;
//
//import jakarta.servlet.*;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//
//import java.io.IOException;
//
////*    Concept:
////*    Limit the number of active requests a user/service can have at the same time.
////*
////*    How it works:
////*    Track number of active requests.
////*    Deny new requests if active count exceeds limit.
////*
////*    Use case: Prevent overloading backend with heavy operations.
////*
////*    Pros: Controls resource usage, not just request rate.
////*
////*    Cons: Doesn’t directly limit request frequency.
////*
////*    Example in real life: Database queries per client.
//
//public class ConcurrentRequestLimiterFilter implements Filter {
//
//    @Override
//    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
//        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
//        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
//
//        String clientIpAddress = httpServletRequest.getRemoteAddr();
//        
//    }
//
//    @Override
//    public void init(FilterConfig filterConfig) throws ServletException {
//        Filter.super.init(filterConfig);
//    }
//
//    @Override
//    public void destroy() {
//        Filter.super.destroy();
//    }
//}
