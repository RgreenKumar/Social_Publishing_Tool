package com.relay.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.relay.api.facebook.FacebookProperties;
import com.relay.api.instagram.InstagramProperties;
import com.relay.api.linkedin.LinkedInProperties;
import com.relay.api.threads.ThreadsProperties;

@Configuration
@EnableConfigurationProperties({
    LinkedInProperties.class,
    FacebookProperties.class,
    InstagramProperties.class,
    ThreadsProperties.class
})
public class AppConfig {
}
