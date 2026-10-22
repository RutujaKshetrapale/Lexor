package com.lexor.feedback.config;

import com.lexor.feedback.entity.FeedbackTag;
import com.lexor.feedback.enums.TagCategory;
import com.lexor.feedback.enums.TagTargetRole;
import com.lexor.feedback.repository.FeedbackTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final FeedbackTagRepository tagRepository;

    @Override
    public void run(String... args) {
        if (tagRepository.count() == 0) {
            log.info("Seeding default feedback tags...");
            List<FeedbackTag> defaultTags = List.of(
                    // Driver evaluation tags (submitted by riders)
                    FeedbackTag.builder().tagCode("FRIENDLY_DRIVER").label("Friendly driver").targetRole(TagTargetRole.DRIVER).category(TagCategory.POSITIVE).displayOrder(1).build(),
                    FeedbackTag.builder().tagCode("PROFESSIONAL").label("Professional").targetRole(TagTargetRole.DRIVER).category(TagCategory.POSITIVE).displayOrder(2).build(),
                    FeedbackTag.builder().tagCode("SAFE_DRIVING").label("Safe driving").targetRole(TagTargetRole.DRIVER).category(TagCategory.POSITIVE).displayOrder(3).build(),
                    FeedbackTag.builder().tagCode("CLEAN_VEHICLE").label("Clean vehicle").targetRole(TagTargetRole.DRIVER).category(TagCategory.POSITIVE).displayOrder(4).build(),
                    FeedbackTag.builder().tagCode("SMOOTH_RIDE").label("Smooth ride").targetRole(TagTargetRole.DRIVER).category(TagCategory.POSITIVE).displayOrder(5).build(),
                    FeedbackTag.builder().tagCode("GOOD_COMMUNICATION").label("Good communication").targetRole(TagTargetRole.BOTH).category(TagCategory.POSITIVE).displayOrder(6).build(),
                    FeedbackTag.builder().tagCode("PUNCTUAL").label("Punctual").targetRole(TagTargetRole.BOTH).category(TagCategory.POSITIVE).displayOrder(7).build(),
                    FeedbackTag.builder().tagCode("ROUTE_CONCERN").label("Route concern").targetRole(TagTargetRole.DRIVER).category(TagCategory.CONCERN).displayOrder(8).build(),
                    FeedbackTag.builder().tagCode("DRIVING_CONCERN").label("Driving concern").targetRole(TagTargetRole.DRIVER).category(TagCategory.CONCERN).displayOrder(9).build(),
                    FeedbackTag.builder().tagCode("VEHICLE_CLEANLINESS_CONCERN").label("Vehicle cleanliness concern").targetRole(TagTargetRole.DRIVER).category(TagCategory.CONCERN).displayOrder(10).build(),
                    FeedbackTag.builder().tagCode("UNPROFESSIONAL_BEHAVIOR").label("Unprofessional behavior").targetRole(TagTargetRole.BOTH).category(TagCategory.CONCERN).displayOrder(11).build(),
                    FeedbackTag.builder().tagCode("OTHER").label("Other").targetRole(TagTargetRole.BOTH).category(TagCategory.GENERAL).displayOrder(12).build(),

                    // Rider evaluation tags (submitted by drivers)
                    FeedbackTag.builder().tagCode("RESPECTFUL").label("Respectful").targetRole(TagTargetRole.RIDER).category(TagCategory.POSITIVE).displayOrder(13).build(),
                    FeedbackTag.builder().tagCode("PUNCTUAL_PICKUP").label("Punctual at pickup").targetRole(TagTargetRole.RIDER).category(TagCategory.POSITIVE).displayOrder(14).build(),
                    FeedbackTag.builder().tagCode("CLEAR_PICKUP_INSTRUCTIONS").label("Clear pickup instructions").targetRole(TagTargetRole.RIDER).category(TagCategory.POSITIVE).displayOrder(15).build(),
                    FeedbackTag.builder().tagCode("COMMUNICATION_ISSUE").label("Communication issue").targetRole(TagTargetRole.RIDER).category(TagCategory.CONCERN).displayOrder(16).build(),
                    FeedbackTag.builder().tagCode("PICKUP_DELAY").label("Pickup delay").targetRole(TagTargetRole.RIDER).category(TagCategory.CONCERN).displayOrder(17).build(),
                    FeedbackTag.builder().tagCode("INAPPROPRIATE_BEHAVIOR").label("Inappropriate behavior").targetRole(TagTargetRole.RIDER).category(TagCategory.CONCERN).displayOrder(18).build(),
                    FeedbackTag.builder().tagCode("SAFETY_CONCERN").label("Safety concern").targetRole(TagTargetRole.BOTH).category(TagCategory.CONCERN).displayOrder(19).build()
            );
            tagRepository.saveAll(defaultTags);
            log.info("Seeded {} feedback tags successfully.", defaultTags.size());
        }
    }
}
