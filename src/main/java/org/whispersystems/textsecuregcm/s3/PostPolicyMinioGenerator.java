package org.whispersystems.textsecuregcm.s3;

import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.errors.MinioException;
import org.joda.time.DateTime;

import java.util.Map;

public class PostPolicyMinioGenerator {

    private final String region;
    private final String bucket;
    private MinioClient client = null;

    public PostPolicyMinioGenerator(String region, String bucket, MinioClient client) {
        this.region = region;
        this.bucket = bucket;
        this.client = client;
    }

    public Map<String, String> createFor(DateTime now, String object, String contentType, String contentEncoding) {
        try {
            PostPolicy policy = new PostPolicy(bucket, object, now.plusMinutes(30));
            if (contentType != null)
                policy.setContentType(contentType);
            if (contentEncoding != null)
                policy.setContentEncoding(contentEncoding);
            return client.presignedPostPolicy(policy);
        } catch (MinioException ex) {
            System.out.println("Minio error when create policy");
            ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
