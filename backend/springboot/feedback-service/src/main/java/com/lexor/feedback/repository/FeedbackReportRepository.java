package com.lexor.feedback.repository;

import com.lexor.feedback.entity.FeedbackReport;
import com.lexor.feedback.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeedbackReportRepository extends JpaRepository<FeedbackReport, Long> {

    Optional<FeedbackReport> findByReportUuid(String reportUuid);

    boolean existsByFeedbackIdAndReporterUuid(Long feedbackId, String reporterUuid);

    long countByFeedbackId(Long feedbackId);

    Page<FeedbackReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    Page<FeedbackReport> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
