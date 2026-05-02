package com.dutchlearn.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TopicUpdateRequestDTO
 * Data Transfer Object for updating the topic of a session
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicUpdateRequestDTO {
    private String topic;
}
