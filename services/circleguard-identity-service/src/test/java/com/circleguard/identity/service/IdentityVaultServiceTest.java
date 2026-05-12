package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityVaultServiceTest {

    private static final String HASH_SALT = "12345678";

    @Mock
    private IdentityMappingRepository repository;

    private IdentityVaultService vaultService;

    @BeforeEach
    void setUp() {
        vaultService = new IdentityVaultService(repository);
        ReflectionTestUtils.setField(vaultService, "hashSalt", HASH_SALT);
    }

    @Test
    void computeHash_shouldReturnConsistentSha256HexStringForSameInput() {
        // Arrange
        String identity = "user@example.com";
        String identityAgain = "user@example.com";

        // Act
        String hash1 = invokeComputeHash(identity);
        String hash2 = invokeComputeHash(identityAgain);

        // Assert
        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
    }

    @Test
    void getOrCreateAnonymousId_shouldReturnExistingMapping_whenHashAlreadyExists() {
        // Arrange
        String realIdentity = "user@example.com";
        UUID existingId = UUID.randomUUID();
        IdentityMapping existingMapping = IdentityMapping.builder()
                .anonymousId(existingId)
                .realIdentity(realIdentity)
                .identityHash(invokeComputeHash(realIdentity))
                .salt("some-salt")
                .build();

        when(repository.findByIdentityHash(any())).thenReturn(Optional.of(existingMapping));

        // Act
        UUID result = vaultService.getOrCreateAnonymousId(realIdentity);

        // Assert
        assertEquals(existingId, result);
        verify(repository, never()).save(any());
    }

    private String invokeComputeHash(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((input + HASH_SALT).getBytes());
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
