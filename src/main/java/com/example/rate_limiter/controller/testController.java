package com.example.rate_limiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class testController {

    @GetMapping("api/v1/fixedWindowFilter/test")
    public String testRateLimiting() {
        return "request Successfull, reached controller!";
    }
}
