package com.airtribe.relayflow.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class DemoTokenFilter extends OncePerRequestFilter {
	private static final String AUTHORIZATION_PREFIX = "Bearer ";
	private final byte[] expectedToken;

	public DemoTokenFilter(@Value("${relay.demo-token:}") String demoToken) {
		this.expectedToken = demoToken.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getServletPath();
		return path.equals("/actuator") || path.startsWith("/actuator/")
				|| path.startsWith("/hooks/") || path.equals("/error");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (!authorized(authorization)) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setHeader("WWW-Authenticate", "Bearer");
			response.setContentType("application/json");
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.getWriter().write("{\"error\":{\"message\":\"Missing or invalid bearer token\",\"code\":\"unauthorized\"}}");
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean authorized(String authorization) {
		if (expectedToken.length == 0 || authorization == null || !authorization.startsWith(AUTHORIZATION_PREFIX)) {
			return false;
		}
		byte[] suppliedToken = authorization.substring(AUTHORIZATION_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
		return suppliedToken.length > 0 && MessageDigest.isEqual(expectedToken, suppliedToken);
	}
}