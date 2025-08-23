package com.example.rate_limiter;

import com.example.rate_limiter.filter.FixedWindowFilter;
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
	public FilterRegistrationBean<FixedWindowFilter> rateLimitingFilter() {
		FilterRegistrationBean<FixedWindowFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new FixedWindowFilter());
		registrationBean.addUrlPatterns("/api/v1/fixedWindowFilter/*"); // Register filter for API endpoints
		return registrationBean;
	}
}
