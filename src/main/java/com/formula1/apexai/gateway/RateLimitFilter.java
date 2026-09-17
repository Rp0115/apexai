package com.formula1.apexai.gateway;

import com.formula1.apexai.config.GatewayProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API Gateway ("The Paddock") — lightweight per-IP rate limiting for /api/**.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

	private final GatewayProperties gatewayProperties;
	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !path.startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String ip = clientIp(request);
		long now = Instant.now().toEpochMilli();
		long windowMs = 60_000L;
		int limit = Math.max(1, gatewayProperties.rateLimitPerMinute());

		Deque<Long> queue = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
		synchronized (queue) {
			while (!queue.isEmpty() && now - queue.peekFirst() > windowMs) {
				queue.pollFirst();
			}
			if (queue.size() >= limit) {
				response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
				response.setContentType("application/json");
				response.getWriter().write("{\"error\":\"Rate limit exceeded — cool down in the paddock.\"}");
				return;
			}
			queue.addLast(now);
		}

		response.setHeader("X-ApexAI-Gateway", "paddock");
		filterChain.doFilter(request, response);
	}

	private String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
