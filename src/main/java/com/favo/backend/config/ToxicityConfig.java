package com.favo.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
@EnableRetry
@EnableConfigurationProperties(ToxicityConfig.ToxicityProperties.class)
public class ToxicityConfig {

    @Bean
    @Qualifier("toxicityRestTemplate")
    public RestTemplate toxicityRestTemplate(ToxicityProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getHttp().getConnectTimeoutMs());
        factory.setReadTimeout(properties.getHttp().getReadTimeoutMs());
        return new RestTemplate(factory);
    }

    @Getter
    @Setter
    @ConfigurationProperties(prefix = "toxicity")
    public static class ToxicityProperties {
        private Thresholds thresholds = new Thresholds();
        private Retry retry = new Retry();
        private Http http = new Http();

        @Getter
        @Setter
        public static class Thresholds {
            private double insult = 0.65;
            private double obscene = 0.70;
            private double toxic = 0.80;
        }

        @Getter
        @Setter
        public static class Retry {
            private int maxAttempts = 3;
            private long delayMs = 300;
            private double multiplier = 2.0;
            private long maxDelayMs = 3000;
        }

        @Getter
        @Setter
        public static class Http {
            private int connectTimeoutMs = 3000;
            private int readTimeoutMs = 5000;
        }
    }
}
