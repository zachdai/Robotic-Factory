package fr.tp.inf112.projects.robotsim.app;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.tp.inf112.projects.canvas.controller.Observer;
import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.CanvasPersistenceManager;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.LocalFactoryModelChangedNotifier;
import tools.jackson.databind.ObjectMapper;

public class RemoteSimulatorController extends SimulatorController {
	private static final Logger LOGGER = Logger.getLogger(RemoteSimulatorController.class.getName());
	private static final int SIMULATION_PORT = 8080;

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper = FactoryJsonMapper.create();
	private final LocalFactoryModelChangedNotifier notifier = new LocalFactoryModelChangedNotifier();
	private volatile boolean animationRunning;
	private String activeFactoryId;
	private FactorySimulationEventConsumer eventConsumer;

	public RemoteSimulatorController(final Factory factoryModel,
								 final CanvasPersistenceManager persistenceManager) {
		super(factoryModel, persistenceManager);
	}

	@Override
	public synchronized void startAnimation() {
		if (animationRunning) {
			return;
		}
		FactorySimulationEventConsumer consumer = null;
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
			consumer = new FactorySimulationEventConsumer(this);
			final FactorySimulationEventConsumer activeConsumer = consumer;
			final Thread consumerThread = new Thread(
						() -> {
							try {
								activeConsumer.consumeMessages();
							}
							catch (final RuntimeException exception) {
								LOGGER.log(Level.SEVERE, "Could not consume simulation events", exception);
							}
						});
			consumerThread.setDaemon(true);
			consumerThread.start();
			consumer.awaitAssignment();
			if (post(factoryId, "start")) {
				animationRunning = true;
				activeFactoryId = factoryId;
				eventConsumer = consumer;
				consumer.start();
			}
			else {
				consumer.stop();
			}
		}
		catch (final IOException | InterruptedException exception) {
			if (consumer != null) {
				consumer.stop();
			}
			LOGGER.log(Level.WARNING, "Could not start remote simulation", exception);
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
		catch (final RuntimeException exception) {
			if (consumer != null) {
				consumer.stop();
			}
			LOGGER.log(Level.WARNING, "Could not start remote simulation", exception);
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
				eventConsumer.stop();
				eventConsumer = null;
				updateCanvas(getFactory(factoryId));
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
	public boolean addObserver(final Observer observer) {
		return notifier.addObserver(observer);
	}

	@Override
	public boolean removeObserver(final Observer observer) {
		return notifier.removeObserver(observer);
	}

	@Override
	public synchronized void setCanvas(final Canvas canvasModel) {
		if (animationRunning) {
			stopAnimation();
		}
		Canvas displayed = canvasModel;
		if (canvasModel != null && canvasModel.getId() != null) {
			try {
				final Factory current = getFactory(canvasModel.getId());
				if (current != null) {
					displayed = current;
				}
			}
			catch (final IOException | InterruptedException exception) {
				LOGGER.log(Level.WARNING, "Could not load current remote factory", exception);
				if (exception instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
			}
		}
		updateCanvas(displayed);
	}

	private void updateCanvas(final Canvas canvasModel) {
		super.setCanvas(canvasModel);
		notifier.notifyObservers();
	}

	public synchronized void setCanvas(final String factoryJson) {
		if (!animationRunning) {
			return;
		}
		final Factory factory = objectMapper.readValue(factoryJson, Factory.class);
		if (factory.getId().equals(activeFactoryId)) {
			updateCanvas(factory);
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
		if (response.statusCode() == 404) {
			return null;
		}
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
