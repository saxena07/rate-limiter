package com.example.rate_limiter;

import com.example.rate_limiter.filter.FixedWindowCounterFilter;
import com.example.rate_limiter.filter.SlidingWindowCounterFilter;
import com.example.rate_limiter.filter.SlidingWindowLogFilter;
import com.example.rate_limiter.filter.TokenBucketFilter;
import com.fasterxml.jackson.databind.util.TokenBufferReadContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RateLimiterApplication {

	public static void main(String[] args) {
		SpringApplication.run(RateLimiterApplication.class, args);
	}

//	TODO: Introduce redis integration and store in that memory instead of local memory

	@Bean
	public FilterRegistrationBean<FixedWindowCounterFilter> fixedWindowCounterFilter() {
		FilterRegistrationBean<FixedWindowCounterFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new FixedWindowCounterFilter());
		registrationBean.addUrlPatterns("/api/v1/fixedWindowCounter/*"); // Register filter for API endpoints
		return registrationBean;
	}

	@Bean
	public FilterRegistrationBean<SlidingWindowCounterFilter> slidingWindowCounterFilter() {
		FilterRegistrationBean<SlidingWindowCounterFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new SlidingWindowCounterFilter());
		registrationBean.addUrlPatterns("/api/v1/slidingWindowCounter/*");
		return registrationBean;
	}

	@Bean
	public FilterRegistrationBean<SlidingWindowLogFilter> slidingWindowLogFilter() {
		FilterRegistrationBean<SlidingWindowLogFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new SlidingWindowLogFilter());
		registrationBean.addUrlPatterns("/api/v1/slidingWindowLog/*");
		return registrationBean;
	}

	@Bean
	public FilterRegistrationBean<TokenBucketFilter> tokenBucketFilter() {
		FilterRegistrationBean<TokenBucketFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new TokenBucketFilter());
		registrationBean.addUrlPatterns("/api/v1/tokenBucket/*");
		return registrationBean;
	}
}
