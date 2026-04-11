package com.dutchlearn.repository;

import com.dutchlearn.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * FeedbackRepository
 * Data access layer for Feedback entities
 */
@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
}
