package org.itech.labSampleTracker.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;

class ConnectionLogRulesTest {

	private static final UsernamePasswordAuthenticationToken AUTH =
			new UsernamePasswordAuthenticationToken("agent", "x");

	@Test
	void mobileApisAreMobileChannel() {
		assertEquals("mobile", ConnectionLogService.channelOf("/api_v2/auth/login"));
		assertEquals("mobile", ConnectionLogService.channelOf("/api_v2/sync/samples"));
		assertEquals("mobile", ConnectionLogService.channelOf("/api/tracker/samples"));
	}

	@Test
	void everythingElseIsWeb() {
		assertEquals("web", ConnectionLogService.channelOf("/login"));
		assertEquals("web", ConnectionLogService.channelOf("/dashboard/data/series"));
		assertEquals("web", ConnectionLogService.channelOf("/api_v2x"));
		assertEquals("web", ConnectionLogService.channelOf(null));
	}

	@Test
	void failureOutcomes() {
		assertEquals("BAD_CREDENTIALS", ConnectionLogListener.outcomeOf(
				new AuthenticationFailureBadCredentialsEvent(AUTH, new BadCredentialsException("x"))));
		assertEquals("LOCKED", ConnectionLogListener.outcomeOf(
				new AuthenticationFailureLockedEvent(AUTH, new LockedException("x"))));
		assertEquals("DISABLED", ConnectionLogListener.outcomeOf(
				new AuthenticationFailureDisabledEvent(AUTH, new DisabledException("x"))));
	}
}
