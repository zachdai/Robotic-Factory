package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import fr.tp.inf112.projects.canvas.controller.Observer;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.FactoryModelChangedNotifier;

public class KafkaFactoryModelChangeNotifier implements FactoryModelChangedNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaFactoryModelChangeNotifier.class);

    private final Factory factoryModel;
    private final KafkaTemplate<String, Factory> simulationEventTemplate;
    private final String topicName;

    public KafkaFactoryModelChangeNotifier(final Factory factoryModel,
                                           final KafkaTemplate<String, Factory> simulationEventTemplate) {
        this.factoryModel = factoryModel;
        this.simulationEventTemplate = simulationEventTemplate;
        topicName = "simulation-" + factoryModel.getId();
        new KafkaAdmin(simulationEventTemplate.getProducerFactory().getConfigurationProperties())
                .createOrModifyTopics(TopicBuilder.name(topicName).build());
    }

    @Override
    public void notifyObservers() {
        final Message<Factory> factoryMessage = MessageBuilder.withPayload(factoryModel)
                .setHeader(KafkaHeaders.TOPIC, topicName)
                .build();
        simulationEventTemplate.send(factoryMessage).whenComplete((result, exception) -> {
            if (exception != null) {
                LOGGER.error("Could not publish simulation event to {}.", topicName, exception);
            }
        });
    }

    @Override
    public boolean addObserver(final Observer observer) {
        return false;
    }

    @Override
    public boolean removeObserver(final Observer observer) {
        return false;
    }
}
