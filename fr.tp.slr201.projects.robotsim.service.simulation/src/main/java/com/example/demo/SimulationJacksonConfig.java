package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import fr.tp.inf112.projects.robotsim.app.FactoryJsonMapper;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SimulationJacksonConfig {
	@Bean
	@Primary
	public JsonMapper jsonMapper() {
		return FactoryJsonMapper.create();
	}
}
