package com.medrag.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
public class InfrastructureConfig {
    @Bean
    S3Client s3Client(MedRagProperties properties) {
        MedRagProperties.Storage storage = properties.storage();
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(storage.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storage.pathStyleAccessEnabled())
                        .build())
                .httpClient(UrlConnectionHttpClient.create());

        if (storage.endpoint() != null && !storage.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(storage.endpoint()));
        }
        if (storage.useDefaultCredentials()) {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        } else {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())
            ));
        }
        return builder.build();
    }

    @Bean
    WebClient aiWebClient(MedRagProperties properties) {
        HttpClient client = HttpClient.create()
                .responseTimeout(properties.ai().responseTimeout())
                .option(
                        io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.ai().connectTimeout().toMillis())
                );
        return WebClient.builder()
                .baseUrl(properties.ai().baseUrl().toString())
                .clientConnector(new ReactorClientHttpConnector(client))
                .build();
    }
}
