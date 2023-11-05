package com.github.yuliyadzemidovich.ratelimiter.filter;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@Component
@Slf4j
public class RateLimiterFilter extends OncePerRequestFilter {

    @Value("${limiter.max_requests}")
    private Long maxRequests;
    @Value("${limiter.period}")
    private Long period;

    private static volatile Long rateLimiterBucket = 0L;

    @PostConstruct
    public void init() {
        if (rateLimiterBucket == null) {
            synchronized (this) {
                if (rateLimiterBucket == null) {
                    log.info("Initializing rate limiter bucket with init value of {}", maxRequests);
                    rateLimiterBucket = maxRequests;
                }
            }
        }
    }

    @Scheduled(fixedRate = 60 * 1000)
    public void refillBucket() {
        synchronized (this) {
            if (log.isDebugEnabled()) {
                log.debug("Refilling the rate limiter bucket. Old value={}, new value={}", rateLimiterBucket, maxRequests);
            }
            rateLimiterBucket = maxRequests;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.contains("/actuator") && exceedRateLimit()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean exceedRateLimit() {
        // avoid unnecessary synchronization if bucket is already empty
        if (rateLimiterBucket <= 0) {
            log.info("Exceeded rate limit - bucket value is {}", rateLimiterBucket);
            return true;
        }
        synchronized (this) {
            if (rateLimiterBucket <= 0) {
                log.info("Exceeded rate limit - bucket value is {}", rateLimiterBucket);
                return true;
            } else {
                rateLimiterBucket--;
                log.info("Current rate limiter bucket value is {}", rateLimiterBucket);
            }
        }
        return false;
    }
}
