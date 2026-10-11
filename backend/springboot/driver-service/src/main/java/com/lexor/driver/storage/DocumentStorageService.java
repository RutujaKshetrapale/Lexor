package com.lexor.driver.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentStorageService {

    StoredDocument store(MultipartFile file);

    Resource loadAsResource(String filePath);

    void delete(String filePath);
}
