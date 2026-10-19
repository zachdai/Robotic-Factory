package com.example.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.tp.inf112.projects.robotsim.model.Factory;

@RestController
@RequestMapping("/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(final SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/{factoryId}/start")
    public boolean startSimulation(@PathVariable("factoryId") final String factoryId) {
        return simulationService.startSimulation(factoryId);
    }

    @GetMapping("/{factoryId}")
    public ResponseEntity<Factory> getFactory(@PathVariable("factoryId") final String factoryId) {
        return ResponseEntity.ofNullable(simulationService.getFactory(factoryId));
    }

    @PostMapping("/{factoryId}/stop")
    public boolean stopSimulation(@PathVariable("factoryId") final String factoryId) {
        return simulationService.stopSimulation(factoryId);
    }
}
