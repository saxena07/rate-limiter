package com.example.rate_limiter;

import com.example.rate_limiter.filter.FixedWindowCounterFilter;
import com.example.rate_limiter.filter.SlidingWindowLogFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RateLimiterApplication {

	public static void main(String[] args) {
		SpringApplication.run(RateLimiterApplication.class, args);
	}

	@Bean
	public FilterRegistrationBean<FixedWindowCounterFilter> rateLimitingFilter() {
		FilterRegistrationBean<FixedWindowCounterFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new FixedWindowCounterFilter());
		registrationBean.addUrlPatterns("/api/v1/fixedWindowCounter/*"); // Register filter for API endpoints
		return registrationBean;
	}

	@Bean
	public FilterRegistrationBean<SlidingWindowLogFilter> anotherFilter() {
		FilterRegistrationBean<SlidingWindowLogFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new SlidingWindowLogFilter());
		registrationBean.addUrlPatterns("/api/v1/slidingWindowLog/*");
		return registrationBean;
	}
}
