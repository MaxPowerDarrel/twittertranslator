package com.superdarrel;

import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.util.Base64;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import org.apache.commons.lang3.StringEscapeUtils;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class Handler {

    private final String twitterConsumer;
    private final String twitterConsumerSecret;
    private final String twitterAccessToken;
    private final String twitterAccessTokenSecret;
    private final String twitterAccountToTranslate;
    private final String googleTranslateAPIKey;
    private final S3Client s3Client;

    private static final int TWEET_LENGTH = 120;

    public Handler() {
        this.twitterConsumer = decryptKey("TwitterConsumer");
        this.twitterConsumerSecret = decryptKey("TwitterConsumerSecret");
        this.twitterAccessToken = decryptKey("TwitterAccessToken");
        this.twitterAccessTokenSecret = decryptKey("TwitterAccessTokenSecret");
        this.googleTranslateAPIKey = decryptKey("GoogleTranslateAPIKey");
        this.twitterAccountToTranslate = System.getenv("TwitterAccountToTranslate");
        this.s3Client = new S3Client();
    }

    private void checkTwitter() {
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setPrettyDebugEnabled(true);
        configurationBuilder.setOAuthConsumerKey(twitterConsumer);
        configurationBuilder.setOAuthConsumerSecret(twitterConsumerSecret);
        configurationBuilder.setOAuthAccessToken(twitterAccessToken);
        configurationBuilder.setOAuthAccessTokenSecret(twitterAccessTokenSecret);

        TwitterFactory factory = new TwitterFactory(configurationBuilder.build());
        Twitter twitter = factory.getInstance();
        Date fromDate = s3Client.getLastTweetTime();
        List<Status> statuses;
        try {
            statuses = twitter.getUserTimeline(twitterAccountToTranslate).stream().filter(status -> status.getCreatedAt().after(fromDate)).collect(Collectors.toList());
            System.out.println("Time" + fromDate.toString());
            System.out.println("Size: " + statuses.size());
            if(!statuses.isEmpty()) {
                s3Client.setLastTweetTime(statuses.get(0).getCreatedAt());
                Collections.reverse(statuses);
                for (Status status : statuses) {
                    String statusText = translateMe(status.getText());
                    while (statusText.length() > TWEET_LENGTH) {
                        StatusUpdate statusUpdate = new StatusUpdate(statusText.substring(0, TWEET_LENGTH + 1) + " ...continued");
                        twitter.updateStatus(statusUpdate);
                        statusText = statusText.substring(TWEET_LENGTH);
                    }
                    StatusUpdate statusUpdate = new StatusUpdate(statusText);
                    statusUpdate.setInReplyToStatusId(status.getId());
                    twitter.updateStatus(statusUpdate);
                }
            }

        } catch (TwitterException te) {
            te.printStackTrace();
        }
    }

    private String translateMe(String in) {
        Translate translator = TranslateOptions.newBuilder().setApiKey(googleTranslateAPIKey).build().getService();
        return StringEscapeUtils.unescapeHtml4(translator.translate(in, Translate.TranslateOption.sourceLanguage("ja"), Translate.TranslateOption.targetLanguage("en")).getTranslatedText());
    }

    private String decryptKey(String key) {
        byte[] encryptedKey = Base64.decode(System.getenv(key));

        AWSKMS client = AWSKMSClientBuilder.defaultClient();

        DecryptRequest request = new DecryptRequest()
                .withCiphertextBlob(ByteBuffer.wrap(encryptedKey));

        ByteBuffer plainTextKey = client.decrypt(request).getPlaintext();
        return new String(plainTextKey.array(), Charset.forName("UTF-8"));
    }

    public void main() {
        Handler handler = new Handler();
        handler.checkTwitter();
    }
}
