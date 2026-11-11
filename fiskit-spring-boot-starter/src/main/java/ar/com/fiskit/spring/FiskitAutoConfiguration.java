package ar.com.fiskit.spring;

import ar.com.fiskit.Fiskit;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** A ready {@link Fiskit} bean from {@code fiskit.*}, unless the application defines its own. */
@AutoConfiguration
@EnableConfigurationProperties(FiskitProperties.class)
public class FiskitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Fiskit fiskit(FiskitProperties properties) {
        return Fiskit.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .maxRetries(properties.maxRetries())
                .timeout(properties.timeout())
                .build();
    }
}
