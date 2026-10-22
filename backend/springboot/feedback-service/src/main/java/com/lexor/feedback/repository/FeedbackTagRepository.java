package com.lexor.feedback.repository;

import com.lexor.feedback.entity.FeedbackTag;
import com.lexor.feedback.enums.TagTargetRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackTagRepository extends JpaRepository<FeedbackTag, Long> {

    Optional<FeedbackTag> findByTagCode(String tagCode);

    List<FeedbackTag> findByTagCodeIn(Collection<String> tagCodes);

    List<FeedbackTag> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<FeedbackTag> findByTargetRoleInAndIsActiveTrueOrderByDisplayOrderAsc(Collection<TagTargetRole> roles);
}
