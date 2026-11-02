package fr.tp.inf112.projects.robotsim.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.tp.inf112.projects.canvas.model.impl.BasicPoint;
import fr.tp.inf112.projects.robotsim.model.Component;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/** Jackson configuration for reading the factory JSON returned by the simulation service. */
public final class FactoryJsonMapper {
	private FactoryJsonMapper() {
	}

	public static JsonMapper create() {
		final PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
				.allowIfSubType(PositionedShape.class.getPackageName())
				.allowIfSubType(Component.class.getPackageName())
				.allowIfSubType(BasicPoint.class.getPackageName())
				.allowIfSubType(ArrayList.class.getName())
				.allowIfSubType(LinkedHashSet.class.getName())
				.build();
		return JsonMapper.builder()
				.activateDefaultTyping(typeValidator, DefaultTyping.NON_FINAL)
				.addMixIn(BasicPoint.class, BasicPointJsonMixin.class)
				.build();
	}

	@JsonAutoDetect(fieldVisibility = Visibility.ANY)
	private abstract static class BasicPointJsonMixin {
		@JsonCreator
		BasicPointJsonMixin(@JsonProperty("xCoordinate") final int xCoordinate,
							@JsonProperty("yCoordinate") final int yCoordinate) {
		}
	}
}
