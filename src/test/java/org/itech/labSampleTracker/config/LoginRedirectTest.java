package org.itech.labSampleTracker.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.savedrequest.RequestCache;

/** Après connexion, on ne revient que vers une vraie page, jamais vers une ressource. */
class LoginRedirectTest {

	private static MockHttpServletRequest get(String path, String accept) {
		MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
		r.setServletPath(path);
		r.addHeader("Accept", accept);
		return r;
	}

	private static boolean saved(MockHttpServletRequest request) {
		RequestCache cache = SecurityConfig.navigationRequestCache();
		cache.saveRequest(request, new MockHttpServletResponse());
		return cache.getRequest(request, new MockHttpServletResponse()) != null;
	}

	@Test
	void versionedFaviconIsNeverTheDestination() {
		org.junit.jupiter.api.Assertions.assertFalse(
				saved(get("/favicon-a90e671cebedb9bef1fb5353e7c5a7a1.ico", "image/avif,image/webp,*/*")));
	}

	@Test
	void ajaxAndFontsAreNotDestinations() {
		org.junit.jupiter.api.Assertions.assertFalse(saved(get("/dashboard/data/funnel", "application/json")));
		org.junit.jupiter.api.Assertions.assertFalse(saved(get("/fonts/x.woff2", "*/*")));
	}

	@Test
	void aPageOpenedFromALinkIsKept() {
		MockHttpServletRequest page = get("/sample", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		RequestCache cache = SecurityConfig.navigationRequestCache();
		cache.saveRequest(page, new MockHttpServletResponse());
		assertNotNull(cache.getRequest(page, new MockHttpServletResponse()));
	}

	@Test
	void postsAreNotReplayed() {
		MockHttpServletRequest post = new MockHttpServletRequest("POST", "/sample/update/1");
		post.addHeader("Accept", "text/html");
		org.junit.jupiter.api.Assertions.assertFalse(saved(post));
	}
}
