package org.whispersystems.textsecuregcm.controllers;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.apache.commons.codec.binary.Base64;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.valuehandling.UnwrapValidatedValue;
import org.joda.time.DateTime;
import org.whispersystems.textsecuregcm.configuration.ProfilesConfiguration;
import org.whispersystems.textsecuregcm.entities.Profile;
import org.whispersystems.textsecuregcm.entities.ProfileAvatarUploadAttributes;
import org.whispersystems.textsecuregcm.limits.RateLimiters;
import org.whispersystems.textsecuregcm.s3.PolicySigner;
import org.whispersystems.textsecuregcm.s3.PostPolicyGenerator;
import org.whispersystems.textsecuregcm.s3.PostPolicyMinioGenerator;
import org.whispersystems.textsecuregcm.storage.Account;
import org.whispersystems.textsecuregcm.storage.AccountsManager;
import org.whispersystems.textsecuregcm.util.Pair;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Properties;

import io.dropwizard.auth.Auth;

@Path("/v1/profile")
public class ProfileController {

  private final RateLimiters     rateLimiters;
  private final AccountsManager  accountsManager;

//  private final PolicySigner        policySigner;
//  private final PostPolicyGenerator policyGenerator;
  private final PostPolicyMinioGenerator policyMinioGenerator;

//  private final AmazonS3            s3client;
  private MinioClient               minioClient;
  private final String              bucket;

  public ProfileController(RateLimiters rateLimiters,
                           AccountsManager accountsManager,
                           ProfilesConfiguration profilesConfiguration)
  {
//    AWSCredentials         credentials         = new BasicAWSCredentials(profilesConfiguration.getAccessKey(), profilesConfiguration.getAccessSecret());
//    AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);

    this.rateLimiters       = rateLimiters;
    this.accountsManager    = accountsManager;
    this.bucket             = profilesConfiguration.getBucket();
//    this.s3client           = AmazonS3Client.builder()
//                                            .withCredentials(credentialsProvider)
//                                            .withRegion(profilesConfiguration.getRegion())
//                                            .build();

    // Create minio fake aws s3
    Properties properties = new Properties();
    try {
      properties.load(PostPolicyMinioGenerator.class.getClassLoader().getResourceAsStream("config.properties"));
      minioClient = new MinioClient(properties.getProperty("endpoint", "https://dev-fone.fosec.vn"),
                                    profilesConfiguration.getAccessKey(),
                                    profilesConfiguration.getAccessSecret());
      boolean exist = minioClient.bucketExists(bucket);
      if (!exist)
        minioClient.makeBucket(bucket);
    } catch (MinioException ex) {
      System.out.println("Minio init error");
      ex.printStackTrace();
    } catch (Exception ex) {
      ex.printStackTrace();
    }

//    this.policyGenerator  = new PostPolicyGenerator(profilesConfiguration.getRegion(),
//                                                    profilesConfiguration.getBucket(),
//                                                    profilesConfiguration.getAccessKey());
//
//    this.policySigner     = new PolicySigner(profilesConfiguration.getAccessSecret(),
//                                             profilesConfiguration.getRegion());

    this.policyMinioGenerator = new PostPolicyMinioGenerator(profilesConfiguration.getRegion(),
                                                             profilesConfiguration.getBucket(),
                                                             minioClient);
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{number}")
  public Profile getProfile(@Auth Account account,
                            @PathParam("number") String number,
                            @QueryParam("ca") boolean useCaCertificate)
      throws RateLimitExceededException
  {
    rateLimiters.getProfileLimiter().validate(account.getNumber());

    Optional<Account> accountProfile = accountsManager.get(number);

    if (!accountProfile.isPresent()) {
      throw new WebApplicationException(Response.status(404).build());
    }

    return new Profile(accountProfile.get().getName(),
                       accountProfile.get().getAvatar(),
                       accountProfile.get().getIdentityKey());
  }

  @Timed
  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/name/{name}")
  public void setProfile(@Auth Account account, @PathParam("name") @UnwrapValidatedValue(true) @Length(min = 72,max= 72) Optional<String> name) {
    account.setName(name.orNull());
    accountsManager.update(account);
  }


  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/form/avatar")
  public ProfileAvatarUploadAttributes getAvatarUploadForm(@Auth Account account) {
    String               previousAvatar = account.getAvatar();
    ZonedDateTime        now            = ZonedDateTime.now(ZoneOffset.UTC);
    String               objectName     = generateAvatarObjectName();

//    Pair<String, String> policy         = policyGenerator.createFor(now, objectName);
//    String               signature      = policySigner.getSignature(now, policy.second());

    // Gen fake policy and signature for minio
    Map<String, String>  policyMinio    = policyMinioGenerator.createFor(DateTime.now(), objectName, null, null);

    if (previousAvatar != null && previousAvatar.startsWith("profiles/")) {

//      s3client.deleteObject(bucket, previousAvatar);

      // Delete in minio instead of s3
      try {
        minioClient.removeObject(bucket, previousAvatar);
      } catch (MinioException ex) {
        System.out.println("Minio error when delete old avatar");
        ex.printStackTrace();
      } catch (Exception ex) {
        System.out.println("Error occur when delete old avatar");
        ex.printStackTrace();
      }
    }

    account.setAvatar(objectName);
    accountsManager.update(account);

//    return new ProfileAvatarUploadAttributes(objectName, policy.first(), "private", "AWS4-HMAC-SHA256",
//                                             now.format(PostPolicyGenerator.AWS_DATE_TIME), policy.second(), signature);
    return new ProfileAvatarUploadAttributes(objectName, policyMinio.get("x-amz-credential"), "private",
                                             policyMinio.get("x-amz-algorithm"), policyMinio.get("x-amz-date"),
                                             policyMinio.get("policy"), policyMinio.get("x-amz-signature"));
  }

  private String generateAvatarObjectName() {
    byte[] object = new byte[16];
    new SecureRandom().nextBytes(object);

    return "profiles/" + Base64.encodeBase64URLSafeString(object);
  }
}
