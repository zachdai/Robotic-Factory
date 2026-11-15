package fr.tp.inf112.projects.robotsim.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.tp.inf112.projects.robotsim.model.motion.Motion;

import fr.tp.inf112.projects.canvas.controller.Observable;
import fr.tp.inf112.projects.canvas.controller.Observer;
import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.Figure;
import fr.tp.inf112.projects.canvas.model.Style;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

public class Factory extends Component implements Canvas, Observable {

	private static final long serialVersionUID = 5156526483612458192L;
	
	private static final ComponentStyle DEFAULT = new ComponentStyle(5.0f);

    @JsonManagedReference
    @JsonProperty
    private final List<Component> components;

	@JsonIgnore
	private transient FactoryModelChangedNotifier notifier;

	@JsonIgnore
	private transient volatile boolean simulationStarted;

	private transient List<Thread> componentThreads;

	@JsonProperty
	private int pathResolution;

	public Factory(final int width,
				   final int height,
				   final String name,
				   final int pathResolution) {
		super(null, new RectangularShape(0, 0, width, height), name);
		
		this.pathResolution = pathResolution;
		components = new ArrayList<>();
		notifier = new LocalFactoryModelChangedNotifier();
		simulationStarted = false;
	}

	public Factory() {
		this(0, 0, null, 1);
	}
	
	@JsonIgnore
	public List<Observer> getObservers() {
		return ((LocalFactoryModelChangedNotifier) getNotifier()).getObservers();
	}

	@JsonIgnore
	public FactoryModelChangedNotifier getNotifier() {
		if (notifier == null) {
			notifier = new LocalFactoryModelChangedNotifier();
		}
		return notifier;
	}

	public void setNotifier(final FactoryModelChangedNotifier notifier) {
		this.notifier = notifier;
	}

	@JsonIgnore
	protected List<Thread> getComponentThreads() {
		if (componentThreads == null) {
			componentThreads = new ArrayList<>();
		}

		return componentThreads;
	}

	@Override
	public boolean addObserver(Observer observer) {
		return getNotifier().addObserver(observer);
	}

	@Override
	public boolean removeObserver(Observer observer) {
		return getNotifier().removeObserver(observer);
	}
	
	public void notifyObservers() {
		getNotifier().notifyObservers();
	}
	
	public boolean addComponent(final Component component) {
		if (components.add(component)) {
			notifyObservers();
			
			return true;
		}
		
		return false;
	}

	public boolean removeComponent(final Component component) {
		if (components.remove(component)) {
			notifyObservers();
			
			return true;
		}
		
		return false;
	}

	@JsonIgnore
	protected List<Component> getComponents() {
		return components;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	@JsonIgnore
	public Collection<Figure> getFigures() {
		return (Collection) components;
	}
	
	public int getPathResolution() {
		return pathResolution;
	}

	public void setPathResolution(int pathResolution) {
		this.pathResolution = pathResolution;
	}

	@Override
	public String toString() {
		return super.toString() + " components=" + components + "]";
	}
	
	public boolean isSimulationStarted() {
		return simulationStarted;
	}

	public void startSimulation() {
		if (!isSimulationStarted()) {
			this.simulationStarted = true;
			notifyObservers();

			behave();
		}
	}

	public void stopSimulation() {
		if (isSimulationStarted()) {
			this.simulationStarted = false;

			for (final Thread componentThread : getComponentThreads()) {
				componentThread.interrupt();
			}

			getComponentThreads().clear();

			notifyObservers();
		}
	}

	@Override
	public boolean behave() {
		getComponentThreads().clear();

		for (final Component component : new ArrayList<>(getComponents())) {
			final Thread componentThread = new Thread(component, component.getName());
			getComponentThreads().add(componentThread);
			componentThread.start();
		}

		return !getComponentThreads().isEmpty();
	}

	public synchronized int moveMobileComponentTo(final Component movingComponent,
												 final Position targetPosition) {
		if (targetPosition == null) {
			return 0;
		}

		final PositionedShape targetShape = new RectangularShape(targetPosition.getxCoordinate(),
																targetPosition.getyCoordinate(),
																2,
																2);

		if (hasMobileComponentAt(targetShape, movingComponent)) {
			return 0;
		}

		return new Motion(movingComponent.getPosition(), targetPosition).moveToTarget();
	}
	
	@Override
	public Style getStyle() {
		return DEFAULT;
	}
	
	public boolean hasObstacleAt(final PositionedShape shape) {
		for (final Component component : getComponents()) {
			if (component.overlays(shape) && !component.canBeOverlayed(shape)) {
				return true;
			}
		}
		
		return false;
	}
	
	public boolean hasMobileComponentAt(final PositionedShape shape,
										final Component movingComponent) {
		for (final Component component : getComponents()) {
			if (component != movingComponent && component.isMobile() && component.overlays(shape)) {
				return true;
			}
		}
		
		return false;
	}
	
	public Component getMobileComponentAt(	final Position position,
											final Component ignoredComponent) {
		if (position == null) {
			return null;
		}
		
		return getMobileComponentAt(new RectangularShape(position.getxCoordinate(), position.getyCoordinate(), 2, 2), ignoredComponent);
	}
	
	public Component getMobileComponentAt(	final PositionedShape shape,
											final Component ignoredComponent) {
		if (shape == null) {
			return null;
		}
		
		for (final Component component : getComponents()) {
			if (component != ignoredComponent && component.isMobile() && component.overlays(shape)) {
				return component;
			}
		}
		
		return null;
	}
}
