package fr.tp.inf112.projects.robotsim.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import fr.tp.inf112.projects.canvas.model.Style;
import fr.tp.inf112.projects.canvas.model.impl.RGBColor;
import fr.tp.inf112.projects.robotsim.model.path.FactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.shapes.CircularShape;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

public class Robot extends Component {
	
	private static final long serialVersionUID = -1218857231970296747L;

	private static final Style STYLE = new ComponentStyle(RGBColor.GREEN, RGBColor.BLACK, 3.0f, null);

	private static final Style BLOCKED_STYLE = new ComponentStyle(RGBColor.RED, RGBColor.BLACK, 3.0f, new float[]{4.0f});

	private final Battery battery;
	
	private int speed;
	
	private List<Component> targetComponents;
	
	private transient Iterator<Component> targetComponentsIterator;
	
	private transient Component currTargetComponent;
	
	private transient Iterator<Position> currentPathPositionsIter;
	
	private transient volatile boolean blocked;

	private transient volatile Position blockedTargetPosition;

	private transient boolean steppingAside;
	
	private FactoryPathFinder pathFinder;

	public Robot(final Factory factory,
				 final FactoryPathFinder pathFinder,
				 final CircularShape shape,
				 final Battery battery,
				 final String name ) {
		super(factory, shape, name);
		
		this.pathFinder = pathFinder;
		
		this.battery = battery;
		
		targetComponents = new ArrayList<>();
		currTargetComponent = null;
		currentPathPositionsIter = null;
		speed = 5;
		setBlocked(false);
		blockedTargetPosition = null;
		steppingAside = false;
	}

	@Override
	public String toString() {
		return super.toString() + " battery=" + battery + "]";
	}

	protected int getSpeed() {
		return speed;
	}

	protected void setSpeed(final int speed) {
		this.speed = speed;
	}
	
	public Position getBlockedTargetPosition() {
		return blockedTargetPosition;
	}
	
	private List<Component> getTargetComponents() {
		if (targetComponents == null) {
			targetComponents = new ArrayList<>();
		}
		
		return targetComponents;
	}
	
	public boolean addTargetComponent(final Component targetComponent) {
		return getTargetComponents().add(targetComponent);
	}
	
	public boolean removeTargetComponent(final Component targetComponent) {
		return getTargetComponents().remove(targetComponent);
	}
	
	@Override
	public boolean isMobile() {
		return true;
	}
	
	public boolean isBlocked() {
		return blocked;
	}

	public void setBlocked(boolean blocked) {
		if ( this.blocked != blocked) {
			this.blocked = blocked;
			notifyObservers();
		}
	}

	@Override
	public boolean behave() {
		if (getTargetComponents().isEmpty()) {
			return false;
		}
		
		if (currTargetComponent == null || hasReachedCurrentTarget()) {
			currTargetComponent = nextTargetComponentToVisit();
			
			computePathToCurrentTargetComponent();
		}

		return moveToNextPathPosition() != 0;
	}
		
	private Component nextTargetComponentToVisit() {
		if (targetComponentsIterator == null || !targetComponentsIterator.hasNext()) {
			targetComponentsIterator = getTargetComponents().iterator();
		}
		
		return targetComponentsIterator.hasNext() ? targetComponentsIterator.next() : null;
	}
	
	public boolean isLivelyLocked() {
		final Position blockedPosition = getBlockedTargetPosition();
		if (blockedPosition == null) {
			return false;
		}
		final Component otherRobot = getFactory().getMobileComponentAt(blockedPosition, this);
		return otherRobot instanceof Robot && getPosition().equals(((Robot) otherRobot).getBlockedTargetPosition());
	}
	
	private int moveToNextPathPosition() {
		final Position targetPosition = computeTargetPosition();

		if (targetPosition == null) {
			setBlocked(true);

			return 0;
		}

		int displacement = getFactory().moveMobileComponentTo(this, targetPosition);

		if (displacement != 0) {
			blockedTargetPosition = null;
			setBlocked(false);
			notifyObservers();
		}
		else {
			blockedTargetPosition = targetPosition;

			if (isBlockedByStuckRobot(targetPosition)) {
				if (!computePathToFreeTargetPosition()) {
					displacement = stepAside(findStepPositionTowardsFreeTarget());

					if (displacement != 0) {
						computePathToFreeTargetPosition();
					}
				}
			}
			else if (isLivelyLocked()) {
				displacement = stepAside(findFreeNeighbouringPosition());
			}
		}

		return displacement;
	}

	private int stepAside(final Position freePosition) {
		if (freePosition == null || steppingAside) {
			return 0;
		}
		
		int displacement;
		steppingAside = true;
		
		try {
			blockedTargetPosition = freePosition;
			displacement = moveToNextPathPosition();
		}
		finally {
			steppingAside = false;
		}
		
		if (displacement != 0) {
			computePathToCurrentTargetComponent();
		}
		
		return displacement;
	}
	
	private Position computeTargetPosition() {
		if (blockedTargetPosition != null) {
			return blockedTargetPosition;
		}

		return currentPathPositionsIter.hasNext() ? currentPathPositionsIter.next() : null;
	}

	private boolean isStuck() {
		return isBlocked() && blockedTargetPosition == null;
	}

	private boolean isBlockedByStuckRobot(final Position position) {
		final Component otherComponent = getFactory().getMobileComponentAt(position, this);

		return otherComponent instanceof Robot && ((Robot) otherComponent).isStuck();
	}

	private boolean computePathToFreeTargetPosition() {
		final int resolution = getFactory().getPathResolution();

		for (final Position freeTargetPosition : findFreeTargetPositions()) {
			final Component temporaryTarget = new TargetPositionComponent(freeTargetPosition, resolution);
			final List<Position> pathPositions = pathFinder.findPath(this, temporaryTarget);

			if (!pathPositions.isEmpty() && !crossesStuckRobot(pathPositions)) {
				currentPathPositionsIter = pathPositions.iterator();
				blockedTargetPosition = null;

				return true;
			}
		}

		return false;
	}

	private List<Position> findFreeTargetPositions() {
		final int resolution = getFactory().getPathResolution();
		final PositionedShape targetShape = currTargetComponent.getPositionedShape();
		final int xStart = targetShape.getxCoordinate() - targetShape.getxCoordinate() % resolution;
		final int yStart = targetShape.getyCoordinate() - targetShape.getyCoordinate() % resolution;
		final List<Position> freeTargetPositions = new ArrayList<>();

		for (int xCoordinate = xStart; xCoordinate <= targetShape.getxCoordinate() + targetShape.getWidth(); xCoordinate += resolution) {
			for (int yCoordinate = yStart; yCoordinate <= targetShape.getyCoordinate() + targetShape.getHeight(); yCoordinate += resolution) {
				if (!isFreePosition(xCoordinate, yCoordinate)) {
					continue;
				}

				if (targetShape.overlays(new RectangularShape(xCoordinate, yCoordinate, 2, 2))) {
					freeTargetPositions.add(new Position(xCoordinate, yCoordinate));
				}
			}
		}

		freeTargetPositions.sort((firstPosition, secondPosition) -> distanceTo(firstPosition) - distanceTo(secondPosition));

		return freeTargetPositions;
	}

	private Position findStepPositionTowardsFreeTarget() {
		final List<Position> freeTargetPositions = findFreeTargetPositions();
		
		if (freeTargetPositions.isEmpty()) {
			return null;
		}
		
		final Position freeTargetPosition = freeTargetPositions.get(0);
		final int resolution = getFactory().getPathResolution();
		final int[][] stepDirections = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
		
		Position stepPosition = null;
		int stepDistance = distanceBetween(getPosition(), freeTargetPosition);
		
		for (final int[] stepDirection : stepDirections) {
			final int xCoordinate = getxCoordinate() + stepDirection[0] * resolution;
			final int yCoordinate = getyCoordinate() + stepDirection[1] * resolution;
			
			if (!isFreePosition(xCoordinate, yCoordinate)) {
				continue;
			}
			
			final Position candidatePosition = new Position(xCoordinate, yCoordinate);
			final int candidateDistance = distanceBetween(candidatePosition, freeTargetPosition);
			
			if (candidateDistance < stepDistance) {
				stepDistance = candidateDistance;
				stepPosition = candidatePosition;
			}
		}
		
		return stepPosition;
	}
	
	private int distanceTo(final Position position) {
		return distanceBetween(getPosition(), position);
	}
	
	private int distanceBetween(final Position firstPosition,
								final Position secondPosition) {
		return Math.abs(firstPosition.getxCoordinate() - secondPosition.getxCoordinate())
			 + Math.abs(firstPosition.getyCoordinate() - secondPosition.getyCoordinate());
	}

	private boolean crossesStuckRobot(final List<Position> pathPositions) {
		for (final Position pathPosition : pathPositions) {
			if (isBlockedByStuckRobot(pathPosition)) {
				return true;
			}
		}

		return false;
	}

	private static class TargetPositionComponent extends Component {

		private static final long serialVersionUID = 6318504974947065396L;

		private TargetPositionComponent(final Position position,
										final int size) {
			super(null, new RectangularShape(position.getxCoordinate(), position.getyCoordinate(), size, size), "Target Position");
		}
	}
	
	private Position findFreeNeighbouringPosition() {
		final Position currentPosition = getPosition();
		final Position blockedPosition = getBlockedTargetPosition();
		final int step = getFactory().getPathResolution();
		
		int xDirection = 0;
		int yDirection = 0;
		
		if (blockedPosition != null) {
			xDirection = Integer.signum(blockedPosition.getxCoordinate() - currentPosition.getxCoordinate());
			yDirection = Integer.signum(blockedPosition.getyCoordinate() - currentPosition.getyCoordinate());
		}
		
		final int[][] candidateDirections;
		
		if (xDirection != 0) {
			candidateDirections = new int[][] { { 0, 1 }, { 0, -1 }, { -xDirection, 0 } };
		}
		else if (yDirection != 0) {
			candidateDirections = new int[][] { { 1, 0 }, { -1, 0 }, { 0, -yDirection } };
		}
		else {
			candidateDirections = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
		}
		
		for (final int[] candidateDirection : candidateDirections) {
			final int xCandidate = currentPosition.getxCoordinate() + candidateDirection[0] * step;
			final int yCandidate = currentPosition.getyCoordinate() + candidateDirection[1] * step;
			
			if (isFreePosition(xCandidate, yCandidate)) {
				return new Position(xCandidate, yCandidate);
			}
		}
		
		return null;
	}
	
	private boolean isFreePosition(final int xCoordinate,
								   final int yCoordinate) {
		final Factory factory = getFactory();
		
		if (xCoordinate < 0 || yCoordinate < 0 ||
			xCoordinate + 2 > factory.getWidth() || yCoordinate + 2 > factory.getHeight()) {
			return false;
		}
		
		final PositionedShape shape = new RectangularShape(xCoordinate, yCoordinate, 2, 2);
		
		return !factory.hasObstacleAt(shape) && !factory.hasMobileComponentAt(shape, this);
	}
	
	private void computePathToCurrentTargetComponent() {
		final List<Position> currentPathPositions = pathFinder.findPath(this, currTargetComponent);
		currentPathPositionsIter = currentPathPositions.iterator();
	}
	
	private boolean hasReachedCurrentTarget() {
		return getPositionedShape().overlays(currTargetComponent.getPositionedShape());
	}
	
	@Override
	public boolean canBeOverlayed(final PositionedShape shape) {
		return true;
	}
	
	@Override
	public Style getStyle() {
		return isBlocked() ? BLOCKED_STYLE : STYLE;
	}
}
