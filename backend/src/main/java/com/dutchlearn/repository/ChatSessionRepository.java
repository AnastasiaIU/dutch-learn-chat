package com.dutchlearn.repository;

import com.dutchlearn.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ChatSessionRepository
 * Data access layer for ChatSession entities
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByUserId(Long userId);
    List<ChatSession> findByUserIdOrderByCreatedAtDesc(Long userId);
}
