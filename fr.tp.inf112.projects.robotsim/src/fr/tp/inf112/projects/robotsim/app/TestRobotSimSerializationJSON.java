package fr.tp.inf112.projects.robotsim.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.tp.inf112.projects.canvas.model.impl.BasicPoint;
import fr.tp.inf112.projects.robotsim.model.Area;
import fr.tp.inf112.projects.robotsim.model.Battery;
import fr.tp.inf112.projects.robotsim.model.Component;
import fr.tp.inf112.projects.robotsim.model.Conveyor;
import fr.tp.inf112.projects.robotsim.model.Door;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.Machine;
import fr.tp.inf112.projects.robotsim.model.Robot;
import fr.tp.inf112.projects.robotsim.model.Room;
import fr.tp.inf112.projects.robotsim.model.path.CustomDijkstraFactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.shapes.BasicPolygonShape;
import fr.tp.inf112.projects.robotsim.model.shapes.CircularShape;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TestRobotSimSerializationJSON {
    private static final Logger LOGGER = Logger.getLogger(TestRobotSimSerializationJSON.class.getName());
    private final ObjectMapper objectMapper;
    private final Factory myFactory;

    public TestRobotSimSerializationJSON() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(PositionedShape.class.getPackageName())
                .allowIfSubType(Component.class.getPackageName())
                .allowIfSubType(BasicPoint.class.getPackageName())
                .allowIfSubType(ArrayList.class.getName())
                .allowIfSubType(LinkedHashSet.class.getName())
                .build();
        objectMapper = JsonMapper.builder()
                .activateDefaultTyping(typeValidator, DefaultTyping.NON_FINAL)
                .addMixIn(BasicPoint.class, BasicPointJsonMixin.class)
                .build();
        myFactory = createFactory();
    }

    private Factory createFactory() {
        Factory factory = new Factory(200, 200, "JSON test factory", 5);
        Room room = new Room(factory, new RectangularShape(20, 20, 75, 75), "Room");
        new Door(room, Room.WALL.BOTTOM, 10, 20, true, "Door");
        Area area = new Area(room, new RectangularShape(35, 35, 50, 50), "Area");
        Machine machine = new Machine(area, new RectangularShape(50, 50, 15, 15), "Machine");
        Robot robot = new Robot(factory, new CustomDijkstraFactoryPathFinder(factory, 5),
                new CircularShape(5, 5, 2), new Battery(10), "Robot");
        robot.addTargetComponent(machine);
        BasicPolygonShape polygon = new BasicPolygonShape();
        polygon.addPoint(new BasicPoint(10, 165));
        polygon.addPoint(new BasicPoint(20, 165));
        polygon.addPoint(new BasicPoint(20, 195));
        new Conveyor(factory, polygon, "Conveyor");
        return factory;
    }

    @Test
    public void testSerialization() {
        final String factoryAsJsonString = objectMapper.writeValueAsString(myFactory);
        LOGGER.info(factoryAsJsonString);

        final Factory roundTrip = objectMapper.readValue(factoryAsJsonString, Factory.class);
        LOGGER.info(roundTrip.toString());
        assertEquals(myFactory.getFigures().size(), roundTrip.getFigures().size(),
                "Component count changed after JSON round trip");
        assertEquals("JSON test factory", roundTrip.getName());
        assertEquals(5, roundTrip.getPathResolution());
        assertEquals(200, roundTrip.getWidth());
        assertEquals(200, roundTrip.getHeight());
        assertTrue(roundTrip.getFigures().stream().anyMatch(c -> c instanceof Room));
        assertTrue(roundTrip.getFigures().stream().anyMatch(c -> c instanceof Robot));
        assertTrue(roundTrip.getFigures().stream().anyMatch(c -> c instanceof Machine));
        Robot copiedRobot = (Robot) roundTrip.getFigures().stream().filter(c -> c instanceof Robot).findFirst().orElseThrow();
        Door copiedDoor = (Door) roundTrip.getFigures().stream().filter(c -> c instanceof Door).findFirst().orElseThrow();
        assertTrue(copiedDoor.isOpen());
        Room copiedRoom = (Room) roundTrip.getFigures().stream().filter(c -> c instanceof Room).findFirst().orElseThrow();
        assertEquals(1, copiedRoom.getDoors().size());
        assertEquals(1, copiedRoom.getAreas().size());
        assertEquals(1, copiedRobot.getTargetComponents().size());
        assertSame(roundTrip.getFigures().stream().filter(c -> c instanceof Machine).findFirst().orElseThrow(),
                copiedRobot.getTargetComponents().get(0));
        assertEquals(5, copiedRobot.getPositionedShape().getxCoordinate());
        Conveyor copiedConveyor = (Conveyor) roundTrip.getFigures().stream()
                .filter(c -> c instanceof Conveyor).findFirst().orElseThrow();
        assertEquals(3, ((BasicPolygonShape) copiedConveyor.getPositionedShape()).getPoints().size());
        assertEquals(Set.of("10,165", "20,165", "20,195"),
                ((BasicPolygonShape) copiedConveyor.getPositionedShape()).getPoints().stream()
                        .map(point -> point.getxCoordinate() + "," + point.getyCoordinate())
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(10, copiedRobot.getBattery().getCapacity());
        assertEquals(10, copiedRobot.getBattery().getLevel());
        assertTrue(roundTrip.hasObstacleAt(new RectangularShape(20, 25, 2, 2)));
        assertFalse(copiedRobot.isSimulationStarted());
    }

    @JsonAutoDetect(fieldVisibility = Visibility.ANY)
    private abstract static class BasicPointJsonMixin {
        @JsonCreator
        BasicPointJsonMixin(@JsonProperty("xCoordinate") int xCoordinate,
                            @JsonProperty("yCoordinate") int yCoordinate) {}
    }
}
