package com.lexor.driver.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredDocument {

    private String originalFileName;
    private String storedFileName;
    private String storagePath;
    private String contentType;
    private long fileSize;
}
