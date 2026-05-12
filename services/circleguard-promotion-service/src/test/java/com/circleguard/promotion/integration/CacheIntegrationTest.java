package com.circleguard.promotion.integration;

import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import com.circleguard.promotion.service.HealthStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
class CacheIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public org.springframework.transaction.PlatformTransactionManager transactionManager() {
            return Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        }

        @Bean(name = "neo4jTransactionManager")
        public org.springframework.transaction.PlatformTransactionManager neo4jTransactionManager() {
            return Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        }
    }

    @Autowired
    private HealthStatusService healthStatusService;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private UserNodeRepository userNodeRepository;

    @MockBean
    private Neo4jClient neo4jClient;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private SystemSettingsRepository systemSettingsRepository;

    @MockBean
    private CircleNodeRepository circleNodeRepository;

    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void getCachedStatus_ShouldReturnCachedResultOnSecondCall() {
        String anonymousId = "test-cache-user";
        when(valueOps.get("user:status:" + anonymousId)).thenReturn("ACTIVE");

        String result1 = healthStatusService.getCachedStatus(anonymousId);
        assertEquals("ACTIVE", result1);

        String result2 = healthStatusService.getCachedStatus(anonymousId);
        assertEquals("ACTIVE", result2);

        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOps, times(1)).get("user:status:" + anonymousId);

        Cache cache = cacheManager.getCache("userStatus");
        assertNotNull(cache);
        assertEquals("ACTIVE", cache.get(anonymousId, String.class));
    }

    @Test
    void evictUserCache_ShouldClearCachedEntry() {
        String anonymousId = "test-evict-user";
        when(valueOps.get("user:status:" + anonymousId)).thenReturn("GREEN");

        healthStatusService.getCachedStatus(anonymousId);
        healthStatusService.getCachedStatus(anonymousId);
        verify(valueOps, times(1)).get(anyString());

        healthStatusService.evictUserCache(anonymousId);

        healthStatusService.getCachedStatus(anonymousId);
        verify(valueOps, times(2)).get(anyString());
    }
}
