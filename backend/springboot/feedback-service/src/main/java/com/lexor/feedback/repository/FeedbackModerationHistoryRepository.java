package com.lexor.feedback.repository;

import com.lexor.feedback.entity.FeedbackModerationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackModerationHistoryRepository extends JpaRepository<FeedbackModerationHistory, Long> {

    List<FeedbackModerationHistory> findByFeedbackIdOrderByModeratedAtDesc(Long feedbackId);
}
