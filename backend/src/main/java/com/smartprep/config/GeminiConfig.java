package com.smartprep.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GeminiConfig {

    @Value("${app.gemini.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${app.gemini.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Bean("geminiRestTemplate")
    public RestTemplate geminiRestTemplate() {
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(20);
        connManager.setDefaultMaxPerRoute(10);

        // The TCP connect timeout, which the two timeouts below do not cover.
        // connectionRequestTimeout bounds the wait for a lease from the pool above, and
        // responseTimeout bounds the wait for a response once the request is sent -- neither
        // bounds establishing the socket in the first place. Without this, a Gemini endpoint
        // that accepts no connections falls back to the operating system's TCP retry
        // behaviour, which can block a thread for minutes past any configured timeout.
        //
        // HttpClient 5 takes this on the connection manager; RequestConfig.setConnectTimeout
        // was deprecated in 5.2 and is ignored by the pooling manager.
        connManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(connectTimeoutSeconds))
                .build());

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(timeoutSeconds))
                .setResponseTimeout(Timeout.ofSeconds(timeoutSeconds))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(factory);
    }
}
