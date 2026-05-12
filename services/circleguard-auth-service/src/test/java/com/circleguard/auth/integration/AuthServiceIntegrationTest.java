package com.circleguard.auth.integration;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.repository.LocalUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IdentityClient identityClient;

    @MockBean
    private LdapAuthenticationProvider ldapAuthenticationProvider;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        when(ldapAuthenticationProvider.authenticate(any()))
                .thenThrow(new BadCredentialsException("LDAP not available"));
        when(identityClient.getAnonymousId(anyString()))
                .thenReturn(UUID.randomUUID());
    }

    @Test
    void shouldLoginSuccessfullyWithValidCredentials() throws Exception {
        String rawPassword = "testPassword123";
        userRepository.save(LocalUser.builder()
                .username("testuser")
                .password(passwordEncoder.encode(rawPassword))
                .email("test@example.com")
                .isActive(true)
                .roles(Collections.emptySet())
                .build());

        String body = objectMapper.writeValueAsString(
                Map.of("username", "testuser", "password", rawPassword));

        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.anonymousId").isString())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andReturn();

        String token = result.getResponse().getContentAsString();
        assertNotNull(token);
    }

    @Test
    void shouldReturn401WhenPasswordIsWrong() throws Exception {
        userRepository.save(LocalUser.builder()
                .username("testuser")
                .password(passwordEncoder.encode("correctPassword"))
                .email("test@example.com")
                .isActive(true)
                .roles(Collections.emptySet())
                .build());

        String body = objectMapper.writeValueAsString(
                Map.of("username", "testuser", "password", "wrongPassword"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
