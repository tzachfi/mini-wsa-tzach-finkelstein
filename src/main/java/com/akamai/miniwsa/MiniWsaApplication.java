package com.akamai.miniwsa;

import com.akamai.miniwsa.config.IngestProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(IngestProperties.class)
public class MiniWsaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniWsaApplication.class, args);
    }
}
