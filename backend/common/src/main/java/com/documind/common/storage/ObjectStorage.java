package com.documind.common.storage;

import java.io.InputStream;

public interface ObjectStorage {

    String store(String objectPath, InputStream content, long size, String contentType);

    InputStream read(String objectPath);

    void delete(String objectPath);
}
