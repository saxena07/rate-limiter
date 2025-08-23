package com.example.rate_limiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class testController {

    @GetMapping("api/v1/fixedWindowCounter/test")
    public String testFixedWindowCounterRateLimiting() {
        return "request Successfull for fixed Window, reached controller!";
    }

    @GetMapping("api/v1/slidingWindowLog/test")
    public String testSlidingWindowLogRateLimiting() {
        return "request Successfull fr Sliding Window, reached controller!";
    }
}
