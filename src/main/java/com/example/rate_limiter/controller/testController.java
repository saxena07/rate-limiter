package com.example.rate_limiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class testController {

    @GetMapping("api/v1/fixedWindowCounter/test")
    public String testFixedWindowCounterRateLimiting() {
        return "request Successfull for fixed Window, reached controller!";
    }

    @GetMapping("api/v1/slidingWindowCounter/test")
    public String testSlidingWindowCounterRateLimiting() {
        return "request Successfull for Sliding Window Counter, reached controller!";
    }

    @GetMapping("api/v1/slidingWindowLog/test")
    public String testSlidingWindowLogRateLimiting() {
        return "request Successfull for Sliding Window Log, reached controller!";
    }

    @GetMapping("api/v1/tokenBucket/test")
    public String testTokrnBucketRateLimiting() {
        return "request Successfull for Token Bucket, reached controller!";
    }
}
