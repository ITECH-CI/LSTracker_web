package org.itech.labSampleTracker.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {
	public record LoginRequest(
			@NotBlank @Size(max = 100) String username,
			@NotBlank @Size(max = 200) String password) {
	}

	/** password_reset : mot de passe temporaire à changer (réinitialisation par un administrateur). */
	public record LoginResponse(String access_token, String refresh_token, String role, Long user_id,
			boolean password_reset) {
	}

	public record RefreshRequest(@NotBlank @Size(max = 1024) String refresh_token) {
	}

	public record RefreshResponse(String access_token, String refresh_token) {
	}
}
