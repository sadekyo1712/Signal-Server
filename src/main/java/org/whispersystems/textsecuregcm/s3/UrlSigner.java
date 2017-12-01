/**
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.s3;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import io.minio.MinioClient;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import io.minio.errors.MinioException;
import org.whispersystems.textsecuregcm.configuration.AttachmentsConfiguration;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Properties;

/**
 * Mod to use with Minio
 */
public class UrlSigner {

  private static final long   DURATION = 60 * 60 * 1000;

//  private final AWSCredentials credentials;
  private final MinioCredentials minioCredentials;
  private final String bucket;
  private final Properties properties = new Properties();

  // Helper class for minio service
  private class MinioCredentials {
    private String accessKey;
    private String accessSecret;

    public MinioCredentials(String accessKey, String accessSecret) {
      MinioCredentials.this.accessKey = accessKey;
      MinioCredentials.this.accessSecret = accessSecret;
    }

    public String getAccessKey() {
      return accessKey;
    }

    public String getAccessSecret() {
      return accessSecret;
    }
  }

  public UrlSigner(AttachmentsConfiguration config) {
//    this.credentials = new BasicAWSCredentials(config.getAccessKey(), config.getAccessSecret());
    this.minioCredentials = new MinioCredentials(config.getAccessKey(), config.getAccessSecret());
    this.bucket      = config.getBucket();

    // Load config for minio
    try {
      properties.load(UrlSigner.class.getClassLoader().getResourceAsStream("config.properties"));
    } catch (IOException ioe) {
      System.out.println("Failed when load config file");
      ioe.printStackTrace();
    }
  }

  public URL getPreSignedUrl(long attachmentId, HttpMethod method) {
//    AmazonS3                    client  = new AmazonS3Client(credentials);
//    GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, String.valueOf(attachmentId), method);
//
//    request.setExpiration(new Date(System.currentTimeMillis() + DURATION));
//    request.setContentType("application/octet-stream");
//
//    client.setS3ClientOptions(S3ClientOptions.builder().setAccelerateModeEnabled(true).build());
//
//    return client.generatePresignedUrl(request);

    // Fake aws s3 => minio
    System.out.println("Generate presign url with minio");
    MinioClient minioClient = createMinioClient(minioCredentials);
    int expire = (int) (DURATION / 1000);
    try {
      if (method == HttpMethod.PUT) {
        return new URL(minioClient.presignedPutObject(bucket, String.valueOf(attachmentId), expire));
      } else if (method == HttpMethod.GET) {
        return new URL(minioClient.presignedGetObject(bucket, String.valueOf(attachmentId), expire));
      }
    } catch (MinioException ex) {
      System.out.println("Minio error when generate presign url");
      ex.printStackTrace();
    } catch (Exception ex) {
      System.out.println("Error occur when generate presign url");
      ex.printStackTrace();
    }
    return null;
  }

  private MinioClient createMinioClient(MinioCredentials credentials) {
    MinioClient minioClient = null;
    try {
      minioClient = new MinioClient(properties.getProperty("endpoint", "https://dev-fone.fosec.vn"),
              credentials.getAccessKey(), credentials.getAccessSecret());
    } catch (InvalidPortException ex) {
      System.out.println("Invalid port");
    } catch (InvalidEndpointException ex) {
      System.out.println("Invalid endpoint");
    }
    return minioClient;
  }

}
