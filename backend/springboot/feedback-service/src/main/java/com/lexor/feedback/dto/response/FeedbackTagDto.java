package com.lexor.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexor.feedback.entity.FeedbackTag;
import com.lexor.feedback.enums.TagCategory;
import com.lexor.feedback.enums.TagTargetRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedbackTagDto {

    private String tagCode;
    private String label;
    private TagTargetRole targetRole;
    private TagCategory category;

    public static FeedbackTagDto fromEntity(FeedbackTag tag) {
        if (tag == null) return null;
        return FeedbackTagDto.builder()
                .tagCode(tag.getTagCode())
                .label(tag.getLabel())
                .targetRole(tag.getTargetRole())
                .category(tag.getCategory())
                .build();
    }
}
