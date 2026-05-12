package com.circleguard.form.integration;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FormServiceIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HealthSurveyRepository surveyRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        surveyRepository.deleteAll();
    }

    @Test
    void shouldSubmitSurveyWithAttachmentAndSetPendingStatusAndSendKafkaEvent() {
        Map<String, Object> request = new HashMap<>();
        request.put("anonymousId", UUID.randomUUID().toString());
        request.put("attachmentPath", "/path/to/file.pdf");

        ResponseEntity<HealthSurvey> response = restTemplate.postForEntity(
                "/api/v1/surveys", request, HealthSurvey.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getId());
        assertEquals(ValidationStatus.PENDING, response.getBody().getValidationStatus());
        assertEquals("/path/to/file.pdf", response.getBody().getAttachmentPath());

        HealthSurvey saved = surveyRepository.findById(response.getBody().getId()).orElseThrow();
        assertEquals(ValidationStatus.PENDING, saved.getValidationStatus());
        assertEquals("/path/to/file.pdf", saved.getAttachmentPath());

        verify(kafkaTemplate, atLeastOnce()).send(
                eq("survey.submitted"),
                anyString(),
                any(Map.class));
    }

    @Test
    void shouldSubmitSurveyWithoutAttachmentAndNotSetPendingStatus() {
        Map<String, Object> request = new HashMap<>();
        request.put("anonymousId", UUID.randomUUID().toString());

        ResponseEntity<HealthSurvey> response = restTemplate.postForEntity(
                "/api/v1/surveys", request, HealthSurvey.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getId());
        assertNull(response.getBody().getValidationStatus());
        assertNull(response.getBody().getAttachmentPath());

        HealthSurvey saved = surveyRepository.findById(response.getBody().getId()).orElseThrow();
        assertNull(saved.getValidationStatus());
        assertNull(saved.getAttachmentPath());
    }
}
