package fr.tp.inf112.projects.robotsim.model;

import java.io.IOException;

import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.CanvasChooser;
import fr.tp.inf112.projects.canvas.model.impl.AbstractCanvasPersistenceManager;
import fr.tp.inf112.projects.robotsim.net.PersistenceClient;

public class RemoteFactoryPersistenceManager extends AbstractCanvasPersistenceManager {

	private final PersistenceClient client;

	public RemoteFactoryPersistenceManager(final CanvasChooser canvasChooser,
										   final String host,
										   final int port) {
		super(canvasChooser);
		client = new PersistenceClient(host, port);
	}

	@Override
	public Canvas read(final String canvasId)
	throws IOException {
		final Object response = client.send(canvasId);

		if (!(response instanceof Factory)) {
			throw new IOException("Expected a Factory response but received "
					+ typeName(response) + ".");
		}

		return (Factory) response;
	}

	@Override
	public void persist(final Canvas canvasModel)
	throws IOException {
		if (!(canvasModel instanceof Factory)) {
			throw new IOException("Only Factory models can be persisted remotely.");
		}

		final Object response = client.send(canvasModel);

		if (!Boolean.TRUE.equals(response)) {
			throw new IOException("The persistence server did not confirm the save operation.");
		}
	}

	@Override
	public boolean delete(final Canvas canvasModel) {
		return false;
	}

	private static String typeName(final Object object) {
		return object == null ? "null" : object.getClass().getName();
	}
}
