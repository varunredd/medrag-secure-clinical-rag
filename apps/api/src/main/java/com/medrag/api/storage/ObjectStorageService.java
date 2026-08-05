package com.medrag.api.storage;
import java.io.InputStream;
public interface ObjectStorageService {
    void put(String key, InputStream input, long length, String contentType, String sha256);
    void delete(String key);
}
