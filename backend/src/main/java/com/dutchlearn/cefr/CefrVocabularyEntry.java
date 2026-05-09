package com.dutchlearn.cefr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CefrVocabularyEntry {
    private String word;
    private String level;
}
