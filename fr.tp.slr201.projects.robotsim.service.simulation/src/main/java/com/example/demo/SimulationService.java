package com.example.demo;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.RemoteFactoryPersistenceManager;

@Service
public class SimulationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulationService.class);

    private final RemoteFactoryPersistenceManager persistenceManager;

    @Autowired
    private KafkaTemplate<String, Factory> simulationEventTemplate;

    private final Map<String, Factory> factories = new LinkedHashMap<>();

    @Autowired
    public SimulationService(@Value("${simulation.persistence.host:localhost}") final String host,
                             @Value("${simulation.persistence.port:5051}") final int port) {
        this(new RemoteFactoryPersistenceManager(null, host, port));
    }

    SimulationService(final RemoteFactoryPersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    // Synchronization prevents concurrent requests from starting the same factory twice.
    public synchronized boolean startSimulation(final String factoryId) {
        Factory factory = factories.get(factoryId);
        final boolean newlyLoaded = factory == null;

        try {
            if (newlyLoaded) {
                LOGGER.info("Loading factory {} from the persistence server.", factoryId);
                factory = (Factory) persistenceManager.read(factoryId);
                if (factory == null) {
                    throw new IOException("The persistence server returned no factory.");
                }
                factory.setId(factoryId);
                factories.put(factoryId, factory);
            }

            if (!factory.isSimulationStarted()) {
                if (simulationEventTemplate != null) {
                    factory.setNotifier(new KafkaFactoryModelChangeNotifier(factory, simulationEventTemplate));
                }
                factory.startSimulation();
                LOGGER.info("Started simulation for factory {}.", factoryId);
            }
            return true;
        }
        catch (final IOException | RuntimeException exception) {
            if (factory != null) {
                try {
                    factory.stopSimulation();
                }
                catch (final RuntimeException stopException) {
                    exception.addSuppressed(stopException);
                }
            }
            if (newlyLoaded) {
                factories.remove(factoryId);
            }
            LOGGER.error("Could not start simulation for factory {}.", factoryId, exception);
            return false;
        }
    }

    public synchronized Factory getFactory(final String factoryId) {
        final Factory factory = factories.get(factoryId);
        if (factory == null) {
            LOGGER.warn("Factory {} has not been loaded by the simulation server.", factoryId);
        }
        else {
            LOGGER.debug("Returning current model for factory {}.", factoryId);
        }
        return factory;
    }

    // Keep the stopped model so clients can retrieve its final state and resume it.
    public synchronized boolean stopSimulation(final String factoryId) {
        final Factory factory = factories.get(factoryId);
        if (factory == null) {
            LOGGER.warn("Cannot stop factory {} because it has not been loaded.", factoryId);
            return false;
        }

        try {
            factory.stopSimulation();
            LOGGER.info("Stopped simulation for factory {}.", factoryId);
            return true;
        }
        catch (final RuntimeException exception) {
            LOGGER.error("Could not stop simulation for factory {}.", factoryId, exception);
            return false;
        }
    }

    @PreDestroy
    public synchronized void stopAllSimulations() {
        for (final String factoryId : factories.keySet()) {
            stopSimulation(factoryId);
        }
        factories.clear();
    }
}
