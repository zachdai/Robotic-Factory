package com.example.demo;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.RemoteFactoryPersistenceManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SimulationControllerTests {

    private StubPersistenceManager persistenceManager;
    private SimulationService simulationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        persistenceManager = new StubPersistenceManager();
        simulationService = new SimulationService(persistenceManager);
        mockMvc = MockMvcBuilders.standaloneSetup(new SimulationController(simulationService)).build();
    }

    @AfterEach
    void tearDown() {
        simulationService.stopAllSimulations();
    }

    @Test
    void startLoadsFactoryFromPersistenceAndStartsIt() throws Exception {
        mockMvc.perform(post("/simulation/test.factory/start"))
                .andExpect(status().isOk()).andExpect(content().string("true"));

        assertEquals("test.factory", persistenceManager.requestedId);
        assertEquals(1, persistenceManager.readCount);
        assertTrue(simulationService.getFactory("test.factory").isSimulationStarted());
    }

    @Test
    void repeatedStartDoesNotReloadTheRunningModel() throws Exception {
        simulationService.startSimulation("test.factory");
        final Factory original = simulationService.getFactory("test.factory");

        mockMvc.perform(post("/simulation/test.factory/start"))
                .andExpect(content().string("true"));

        assertEquals(1, persistenceManager.readCount);
        assertSame(original, simulationService.getFactory("test.factory"));
    }

    @Test
    void getReturnsTheLatestLoadedModel() {
        simulationService.startSimulation("test.factory");
        final Factory factory = simulationService.getFactory("test.factory");
        factory.setPathResolution(7);

        // Full model JSON serialization is implemented in the next exercise section.
        final ResponseEntity<Factory> response =
                new SimulationController(simulationService).getFactory("test.factory");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(factory, response.getBody());
        assertEquals(7, response.getBody().getPathResolution());
    }

    @Test
    void unknownFactoryReturnsNotFound() throws Exception {
        mockMvc.perform(get("/simulation/missing.factory"))
                .andExpect(status().isNotFound());
    }

    @Test
    void stopRetainsFinalStateAndAllowsResume() throws Exception {
        simulationService.startSimulation("test.factory");
        final Factory original = simulationService.getFactory("test.factory");

        mockMvc.perform(post("/simulation/test.factory/stop"))
                .andExpect(status().isOk()).andExpect(content().string("true"));
        assertFalse(original.isSimulationStarted());
        assertSame(original, new SimulationController(simulationService)
                .getFactory("test.factory").getBody());
        mockMvc.perform(post("/simulation/test.factory/start"))
                .andExpect(content().string("true"));

        assertSame(original, simulationService.getFactory("test.factory"));
        assertTrue(original.isSimulationStarted());
        assertEquals(1, persistenceManager.readCount);
    }

    @Test
    void stoppingAnUnknownFactoryReturnsFalse() throws Exception {
        mockMvc.perform(post("/simulation/missing.factory/stop"))
                .andExpect(status().isOk()).andExpect(content().string("false"));
    }

    @Test
    void persistenceFailureReturnsFalseWithoutRegisteringFactory() throws Exception {
        persistenceManager.failure = new IOException("Persistence server unavailable.");

        mockMvc.perform(post("/simulation/test.factory/start"))
                .andExpect(status().isOk()).andExpect(content().string("false"));
        assertNull(simulationService.getFactory("test.factory"));
    }

    @Test
    void failedStartIsCleanedUpAndCanBeRetried() throws Exception {
        final Factory brokenFactory = new Factory(100, 100, "Broken factory", 5) {
            @Override
            public boolean behave() {
                throw new IllegalStateException("Could not start component threads.");
            }
        };
        persistenceManager.nextFactory = brokenFactory;

        mockMvc.perform(post("/simulation/test.factory/start"))
                .andExpect(content().string("false"));
        assertFalse(brokenFactory.isSimulationStarted());
        assertNull(simulationService.getFactory("test.factory"));

        persistenceManager.nextFactory = null;
        mockMvc.perform(post("/simulation/test.factory/start"))
                .andExpect(content().string("true"));
        assertEquals(2, persistenceManager.readCount);
    }

    @Test
    void factoriesHaveIndependentSimulationStateAndStopOnShutdown() {
        assertTrue(simulationService.startSimulation("first.factory"));
        assertTrue(simulationService.startSimulation("second.factory"));
        final Factory first = simulationService.getFactory("first.factory");
        final Factory second = simulationService.getFactory("second.factory");

        assertTrue(simulationService.stopSimulation("first.factory"));
        assertFalse(first.isSimulationStarted());
        assertTrue(second.isSimulationStarted());

        simulationService.stopAllSimulations();
        assertFalse(second.isSimulationStarted());
    }

    private static class StubPersistenceManager extends RemoteFactoryPersistenceManager {

        private int readCount;
        private String requestedId;
        private IOException failure;
        private Factory nextFactory;

        StubPersistenceManager() {
            super(null, "localhost", 5051);
        }

        @Override
        public Canvas read(final String canvasId) throws IOException {
            readCount++;
            requestedId = canvasId;
            if (failure != null) {
                throw failure;
            }
            return nextFactory == null ? new Factory(100, 100, "Test factory", 5) : nextFactory;
        }
    }
}
