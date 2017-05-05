package com.superdarrel;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Date;

public class S3Client {

    private final AmazonS3 client;

    public S3Client() {
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard().withCredentials(new DefaultAWSCredentialsProviderChain());
        builder.setRegion("us-west-2");
        client = builder.build();
    }

    public Date getLastTweetTime() {
        try {
            final S3Object dateAsStream = client.getObject("feheroes", "lastTweetTime");
            long date = new ObjectMapper().readValue(dateAsStream.getObjectContent(), Long.class);
            return new Date(date);
        } catch (AmazonS3Exception e) {
            e.printStackTrace();
            return new Date(1493791200L);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setLastTweetTime(Date date) {
        client.putObject("feheroes", "lastTweetTime", Long.toString(date.getTime()));
    }
}

