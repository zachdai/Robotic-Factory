package com.example.demo;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import fr.tp.inf112.projects.robotsim.model.Factory;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SimulationServiceConfig {

    @Bean
    ProducerFactory<String, Factory> producerFactory(final JsonMapper jsonMapper) {
        final Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        return new DefaultKafkaProducerFactory<>(config,
                new StringSerializer(), new JacksonJsonSerializer<>(jsonMapper));
    }

    @Bean
    KafkaTemplate<String, Factory> kafkaTemplate(final ProducerFactory<String, Factory> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
