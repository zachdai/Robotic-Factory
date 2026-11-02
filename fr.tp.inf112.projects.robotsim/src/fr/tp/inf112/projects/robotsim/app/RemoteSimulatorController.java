package fr.tp.inf112.projects.robotsim.app;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.tp.inf112.projects.canvas.controller.Observer;
import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.CanvasPersistenceManager;
import fr.tp.inf112.projects.robotsim.model.Factory;
import tools.jackson.databind.ObjectMapper;

public class RemoteSimulatorController extends SimulatorController {
	private static final Logger LOGGER = Logger.getLogger(RemoteSimulatorController.class.getName());
	private static final int SIMULATION_PORT = 8080;

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper = FactoryJsonMapper.create();
	private volatile boolean animationRunning;
	private Thread updateThread;
	private String activeFactoryId;

	public RemoteSimulatorController(final Factory factoryModel,
								 final CanvasPersistenceManager persistenceManager) {
		super(factoryModel, persistenceManager);
	}

	@Override
	public synchronized void startAnimation() {
		if (animationRunning) {
			return;
		}
		try {
			if (getCanvas().getId() == null) {
				final String newId = getPersistenceManager().getCanvasChooser().newCanvasId();
				if (newId == null) {
					return;
				}
				getCanvas().setId(newId);
				getPersistenceManager().persist(getCanvas());
			}
			final String factoryId = getCanvas().getId();
			if (post(factoryId, "start")) {
				animationRunning = true;
				activeFactoryId = factoryId;
				updateThread = new Thread(() -> updateViewer(factoryId));
				updateThread.setDaemon(true);
				updateThread.start();
			}
		}
		catch (final IOException | InterruptedException exception) {
			LOGGER.log(Level.WARNING, "Could not start remote simulation", exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public synchronized void stopAnimation() {
		if (!animationRunning) {
			return;
		}
		final String factoryId = activeFactoryId;
		try {
			if (post(factoryId, "stop")) {
				animationRunning = false;
				activeFactoryId = null;
				updateThread.interrupt();
				setCanvas(getFactory(factoryId));
			}
		}
		catch (final IOException | InterruptedException exception) {
			LOGGER.log(Level.WARNING, "Could not stop remote simulation", exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public boolean isAnimationRunning() {
		return animationRunning;
	}

	@Override
	public synchronized void setCanvas(final Canvas canvasModel) {
		final Factory oldFactory = (Factory) getCanvas();
		final List<Observer> observers = oldFactory == null ? List.of() : List.copyOf(oldFactory.getObservers());
		super.setCanvas(canvasModel);
		final Factory newFactory = (Factory) getCanvas();
		for (final Observer observer : observers) {
			newFactory.addObserver(observer);
		}
		newFactory.notifyObservers();
	}

	private void updateViewer(final String factoryId) {
		while (animationRunning) {
			try {
				final Factory factory = getFactory(factoryId);
				synchronized (this) {
					if (!animationRunning) {
						return;
					}
					setCanvas(factory);
				}
				Thread.sleep(100);
			}
			catch (final InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
			catch (final IOException exception) {
				LOGGER.log(Level.WARNING, "Could not refresh remote factory", exception);
				return;
			}
		}
	}

	private boolean post(final String factoryId, final String action)
	throws IOException, InterruptedException {
		final HttpRequest request = HttpRequest.newBuilder(uri(factoryId, "/" + action))
				.POST(HttpRequest.BodyPublishers.noBody()).build();
		final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("Simulation service returned HTTP " + response.statusCode());
		}
		return Boolean.parseBoolean(response.body());
	}

	private Factory getFactory(final String factoryId) throws IOException, InterruptedException {
		final HttpRequest request = HttpRequest.newBuilder(uri(factoryId, "")).GET().build();
		final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("Simulation service returned HTTP " + response.statusCode());
		}
		return objectMapper.readValue(response.body(), Factory.class);
	}

	private URI uri(final String factoryId, final String suffix) throws IOException {
		try {
			return new URI("http", null, "localhost", SIMULATION_PORT,
					"/simulation/" + factoryId + suffix, null, null);
		}
		catch (final URISyntaxException exception) {
			throw new IOException("Invalid factory ID", exception);
		}
	}
}
