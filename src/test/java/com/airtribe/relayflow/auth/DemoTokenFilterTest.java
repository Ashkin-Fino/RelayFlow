package com.airtribe.relayflow.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DemoTokenFilterTest {
	private final DemoTokenFilter filter = new DemoTokenFilter("local-test-token");

	@Test
	void allowsRequestWithValidBearerToken() throws Exception {
		MockHttpServletRequest request = request();
		request.addHeader("Authorization", "Bearer local-test-token");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertEquals(200, response.getStatus());
		assertNotNull(chain.getRequest());
	}

	@Test
	void rejectsMissingBearerTokenWithContractErrorBody() throws Exception {
		MockHttpServletRequest request = request();
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(401, response.getStatus());
		assertTrue(response.getContentAsString().contains("\"code\":\"unauthorized\""));
	}

	@Test
	void rejectsIncorrectBearerToken() throws Exception {
		MockHttpServletRequest request = request();
		request.addHeader("Authorization", "Bearer wrong-token");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(401, response.getStatus());
	}

	@Test
	void leavesActuatorHealthPublic() throws Exception {
		MockHttpServletRequest request = request();
		request.setServletPath("/actuator/health");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertEquals(200, response.getStatus());
		assertNotNull(chain.getRequest());
	}

	private MockHttpServletRequest request() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServletPath("/workflows");
		return request;
	}
}
