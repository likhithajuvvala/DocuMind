package com.documind.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;

public class MinioObjectStorage implements ObjectStorage {

    private static final long UNKNOWN_PART_SIZE = -1;

    private final MinioClient client;
    private final ObjectStorageProperties properties;

    public MinioObjectStorage(MinioClient client, ObjectStorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @PostConstruct
    public void ensureBucketExists() {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!exists) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            }
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to prepare bucket " + properties.getBucket(), exception);
        }
    }

    @Override
    public String store(String objectPath, InputStream content, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectPath)
                    .stream(content, size, UNKNOWN_PART_SIZE)
                    .contentType(contentType)
                    .build());
            return objectPath;
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to store object " + objectPath, exception);
        }
    }

    @Override
    public InputStream read(String objectPath) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectPath)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to read object " + objectPath, exception);
        }
    }

    @Override
    public void delete(String objectPath) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectPath)
                    .build());
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to delete object " + objectPath, exception);
        }
    }
}
