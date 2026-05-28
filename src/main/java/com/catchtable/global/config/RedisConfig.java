package com.catchtable.global.config;

import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder;
import io.lettuce.core.metrics.MicrometerOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    public ClientResources lettuceClientResources(MeterRegistry meterRegistry) {
        MicrometerOptions options = MicrometerOptions.create();
        return DefaultClientResources.builder()
                .commandLatencyRecorder(new MicrometerCommandLatencyRecorder(meterRegistry, options))
                .build();
    }
}
