package com.medrag.api.storage;

import com.medrag.api.config.MedRagProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Service
public class S3ObjectStorageService implements ObjectStorageService {
    private final S3Client s3; private final MedRagProperties.Storage storage;
    public S3ObjectStorageService(S3Client s3, MedRagProperties props){this.s3=s3; this.storage=props.storage();}
    public void put(String key, InputStream input, long length, String contentType, String sha256){
        var builder=PutObjectRequest.builder().bucket(storage.documentBucket()).key(key).contentType(contentType)
                .metadata(java.util.Map.of("sha256",sha256));
        if(storage.sseAlgorithm()!=null && !storage.sseAlgorithm().isBlank()){
            builder.serverSideEncryption(storage.sseAlgorithm());
            if(storage.kmsKeyId()!=null && !storage.kmsKeyId().isBlank()) builder.ssekmsKeyId(storage.kmsKeyId());
        }
        s3.putObject(builder.build(), RequestBody.fromInputStream(input,length));
    }
    public void delete(String key){s3.deleteObject(DeleteObjectRequest.builder().bucket(storage.documentBucket()).key(key).build());}
}
