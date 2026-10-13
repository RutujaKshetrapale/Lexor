package com.lexor.vehicle.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredDocument {
    private String originalFileName;
    private String storedFileName;
    private String storagePath;
    private String contentType;
    private Long fileSize;
}
