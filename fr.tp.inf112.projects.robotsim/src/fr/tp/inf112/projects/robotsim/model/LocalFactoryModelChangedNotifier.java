package fr.tp.inf112.projects.robotsim.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import fr.tp.inf112.projects.canvas.controller.Observer;

public class LocalFactoryModelChangedNotifier implements FactoryModelChangedNotifier {
	private final List<Observer> observers = new CopyOnWriteArrayList<>();

	public List<Observer> getObservers() {
		return observers;
	}

	@Override
	public void notifyObservers() {
		for (final Observer observer : getObservers()) {
			observer.modelChanged();
		}
	}

	@Override
	public boolean addObserver(final Observer observer) {
		return observers.add(observer);
	}

	@Override
	public boolean removeObserver(final Observer observer) {
		return observers.remove(observer);
	}
}
