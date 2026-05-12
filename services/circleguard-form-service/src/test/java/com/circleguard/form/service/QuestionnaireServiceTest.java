package com.circleguard.form.service;

import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.repository.QuestionnaireRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionnaireServiceTest {

    @Mock
    private QuestionnaireRepository repository;

    private QuestionnaireService questionnaireService;

    @BeforeEach
    void setUp() {
        questionnaireService = new QuestionnaireService(repository);
    }

    @Test
    void saveQuestionnaire_shouldLinkQuestionsAndPersist() {
        // Arrange
        Question q1 = Question.builder()
                .text("Do you have fever?")
                .type(QuestionType.YES_NO)
                .orderIndex(1)
                .build();

        Question q2 = Question.builder()
                .text("Describe your symptoms")
                .type(QuestionType.TEXT)
                .orderIndex(2)
                .build();

        Questionnaire questionnaire = Questionnaire.builder()
                .title("Health Check")
                .description("Daily health questionnaire")
                .version(1)
                .isActive(true)
                .questions(List.of(q1, q2))
                .build();

        when(repository.save(any())).thenAnswer(invocation -> {
            Questionnaire q = invocation.getArgument(0);
            q.setId(java.util.UUID.randomUUID());
            return q;
        });

        // Act
        Questionnaire result = questionnaireService.saveQuestionnaire(questionnaire);

        // Assert
        assertNotNull(result);
        assertEquals("Health Check", result.getTitle());
        assertEquals(2, result.getQuestions().size());
        result.getQuestions().forEach(q -> assertSame(result, q.getQuestionnaire()));
        verify(repository, times(1)).save(questionnaire);
    }
}
