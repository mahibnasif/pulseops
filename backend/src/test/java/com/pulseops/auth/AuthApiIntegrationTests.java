package com.pulseops.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import com.jayway.jsonpath.JsonPath;
import com.pulseops.support.AbstractIntegrationTest;
import com.pulseops.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AuthApiIntegrationTests extends AbstractIntegrationTest {

	private static final String REGISTER_PAYLOAD = """
			{
			  "firstName": "Ada",
			  "lastName": "Lovelace",
			  "email": "Ada.Lovelace@Example.com",
			  "password": "Correct-Horse-42"
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void cleanDatabase() {
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void registrationHashesPasswordAndReturnsAuthenticatedSession() throws Exception {
		var registration = register();

		registration.andExpect(status().isCreated())
				.andExpect(cookie().exists(AuthController.REFRESH_COOKIE_NAME))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
				.andExpect(header().string(
						HttpHeaders.SET_COOKIE,
						org.hamcrest.Matchers.allOf(
								org.hamcrest.Matchers.containsString("HttpOnly"),
								org.hamcrest.Matchers.containsString("SameSite=Strict"))))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(900))
				.andExpect(jsonPath("$.user.email").value("ada.lovelace@example.com"))
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist());

		var user = userRepository.findByEmail("ada.lovelace@example.com").orElseThrow();
		assertThat(user.getPasswordHash()).startsWith("{bcrypt}");
		assertThat(user.getPasswordHash()).doesNotContain("Correct-Horse-42");
		assertThat(passwordEncoder.matches("Correct-Horse-42", user.getPasswordHash())).isTrue();
		assertThat(user.isEmailVerified()).isFalse();
		assertThat(refreshTokenRepository.count()).isEqualTo(1);
	}

	@Test
	void protectedCurrentUserRequiresAndAcceptsBearerToken() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

		var registration = register().andReturn();
		var accessToken = JsonPath.<String>read(
				registration.getResponse().getContentAsString(),
				"$.accessToken");

		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value("Ada"))
				.andExpect(jsonPath("$.email").value("ada.lovelace@example.com"))
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void loginUsesTheSameSafeErrorForUnknownEmailAndWrongPassword() throws Exception {
		register();

		var wrongPassword = login("ada.lovelace@example.com", "This-Is-Wrong-42")
				.andExpect(status().isUnauthorized())
				.andReturn()
				.getResponse()
				.getContentAsString();
		var unknownEmail = login("unknown@example.com", "This-Is-Wrong-42")
				.andExpect(status().isUnauthorized())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(JsonPath.<String>read(wrongPassword, "$.code"))
				.isEqualTo("INVALID_CREDENTIALS");
		assertThat(JsonPath.<String>read(unknownEmail, "$.code"))
				.isEqualTo("INVALID_CREDENTIALS");
		assertThat(JsonPath.<String>read(wrongPassword, "$.message"))
				.isEqualTo(JsonPath.<String>read(unknownEmail, "$.message"));
	}

	@Test
	void refreshRotatesTokenAndReplayRevokesTheFamily() throws Exception {
		var firstSession = register().andReturn();
		var firstRefresh = requireRefreshCookie(firstSession);

		var refreshedSession = mockMvc.perform(post("/api/v1/auth/refresh")
						.cookie(firstRefresh))
				.andExpect(status().isOk())
				.andExpect(cookie().exists(AuthController.REFRESH_COOKIE_NAME))
				.andReturn();
		var secondRefresh = requireRefreshCookie(refreshedSession);
		assertThat(secondRefresh.getValue()).isNotEqualTo(firstRefresh.getValue());

		mockMvc.perform(post("/api/v1/auth/refresh").cookie(firstRefresh))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

		mockMvc.perform(post("/api/v1/auth/refresh").cookie(secondRefresh))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void logoutRevokesRefreshSessionAndClearsCookie() throws Exception {
		var session = register().andReturn();
		var refreshCookie = requireRefreshCookie(session);

		mockMvc.perform(post("/api/v1/auth/logout").cookie(refreshCookie))
				.andExpect(status().isNoContent())
				.andExpect(cookie().maxAge(AuthController.REFRESH_COOKIE_NAME, 0));

		mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void duplicateRegistrationDoesNotConfirmThatTheEmailExists() throws Exception {
		register();

		register().andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("REGISTRATION_FAILED"))
				.andExpect(jsonPath("$.message")
						.value("An account could not be created with the supplied information."));
	}

	private org.springframework.test.web.servlet.ResultActions register() throws Exception {
		return mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(REGISTER_PAYLOAD));
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password)
			throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "email": "%s",
						  "password": "%s"
						}
						""".formatted(email, password)));
	}

	private Cookie requireRefreshCookie(MvcResult result) {
		var cookie = result.getResponse().getCookie(AuthController.REFRESH_COOKIE_NAME);
		assertThat(cookie).isNotNull();
		return cookie;
	}
}
