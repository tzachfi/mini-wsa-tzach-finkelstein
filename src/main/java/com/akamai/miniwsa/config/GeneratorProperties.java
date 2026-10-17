package com.akamai.miniwsa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.generator")
public record GeneratorProperties(boolean enabled) {}
