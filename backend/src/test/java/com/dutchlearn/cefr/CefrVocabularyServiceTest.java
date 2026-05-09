package com.dutchlearn.cefr;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = CefrVocabularyServiceTest.TestConfig.class)
@TestPropertySource(properties = {
    "cefr.vocabulary.path=cefr/nt2lex-sample.tsv",
    "cefr.vocabulary.format=tsv",
    "cefr.vocabulary.min-entries=1"
})
class CefrVocabularyServiceTest {

    @Autowired
    private CefrVocabularyService vocabularyService;

    @Test
    void loadsNt2LexSampleTsv() {
        assertTrue(vocabularyService.isDataAvailable());
        assertTrue(vocabularyService.getWordsForLevels(EnumSet.of(CefrLevel.A1)).contains("huis"));
        assertTrue(vocabularyService.getWordsForLevels(EnumSet.of(CefrLevel.A2)).contains("fiets"));
        assertTrue(vocabularyService.getWordsForLevels(EnumSet.of(CefrLevel.B1)).contains("omgeving"));
    }

    @Configuration
    static class TestConfig {
        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        CefrTextNormalizer cefrTextNormalizer() {
            return new CefrTextNormalizer();
        }

        @Bean
        CefrVocabularyService cefrVocabularyService(ObjectMapper objectMapper, CefrTextNormalizer normalizer) {
            return new CefrVocabularyService(objectMapper, normalizer);
        }
    }
}
