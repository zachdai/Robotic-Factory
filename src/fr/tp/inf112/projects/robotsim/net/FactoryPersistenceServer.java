package fr.tp.inf112.projects.robotsim.net;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.FactoryPersistenceManager;

public final class FactoryPersistenceServer {

	private static final Logger LOGGER = Logger.getLogger(FactoryPersistenceServer.class.getName());

	private final int port;

	private final FactoryPersistenceManager persistenceManager;

	public FactoryPersistenceServer(final int port) {
		if (port < 1 || port > 65535) {
			throw new IllegalArgumentException("Port must be between 1 and 65535.");
		}

		this.port = port;
		persistenceManager = new FactoryPersistenceManager(null);
	}

	public static void main(final String[] args) {
		final int port;

		try {
			port = args.length == 0
					? PersistenceProtocol.DEFAULT_PORT
					: Integer.parseInt(args[0]);
			new FactoryPersistenceServer(port).run();
		}
		catch (final Exception ex) {
			LOGGER.log(Level.SEVERE, "Could not run the factory persistence server.", ex);
		}
	}

	public void run()
	throws IOException {
		try (ServerSocket serverSocket = new ServerSocket(port)) {
			LOGGER.info(() -> "Factory persistence server listening on port " + port
					+ " in " + Path.of(".").toAbsolutePath().normalize() + ".");

			while (!Thread.currentThread().isInterrupted()) {
				try {
					handleClient(serverSocket.accept());
				}
				catch (final IOException ex) {
					LOGGER.log(Level.WARNING, "Could not process a persistence request.", ex);
				}
			}
		}
	}

	private void handleClient(final Socket clientSocket)
	throws IOException {
		try (Socket socket = clientSocket;
			 ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
			output.flush();

			try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
				final Object response;

				try {
					response = processRequest(input.readObject());
				}
				catch (final Exception ex) {
					LOGGER.log(Level.WARNING, "Persistence request failed.", ex);
					output.writeObject(asIOException(ex));
					output.flush();
					return;
				}

				output.writeObject(response);
				output.flush();
			}
		}
	}

	private Object processRequest(final Object request)
	throws IOException {
		if (request instanceof String) {
			final String canvasId = validateCanvasId((String) request);
			LOGGER.info(() -> "Reading factory " + canvasId + ".");
			return persistenceManager.read(canvasId);
		}

		if (request instanceof Factory) {
			final Factory factory = (Factory) request;
			final String canvasId = validateCanvasId(factory.getId());
			factory.setId(canvasId);
			LOGGER.info(() -> "Persisting factory " + canvasId + ".");
			persistenceManager.persist(factory);
			return Boolean.TRUE;
		}

		if (request == PersistenceCommand.LIST_FILES) {
			return listFactoryFiles();
		}

		throw new IOException("Unsupported persistence request type: " + typeName(request) + ".");
	}

	private String[] listFactoryFiles()
	throws IOException {
		final File[] files = new File(".").listFiles(file -> file.isFile()
				&& file.getName().endsWith("." + PersistenceProtocol.FACTORY_FILE_EXTENSION));

		if (files == null) {
			throw new IOException("Could not list files in the server working directory.");
		}

		return Arrays.stream(files)
				.map(File::getName)
				.sorted()
				.toArray(String[]::new);
	}

	private static String validateCanvasId(final String canvasId)
	throws IOException {
		if (canvasId == null || canvasId.isBlank()) {
			throw new IOException("Factory identifier cannot be empty.");
		}

		try {
			final Path path = Path.of(canvasId);

			if (path.isAbsolute() || path.getNameCount() != 1) {
				throw new IOException("Factory identifier must be a file name, not a path: "
						+ canvasId + ".");
			}
		}
		catch (final InvalidPathException ex) {
			throw new IOException("Invalid factory identifier: " + canvasId + ".", ex);
		}

		return canvasId;
	}

	private static IOException asIOException(final Exception exception) {
		return exception instanceof IOException
				? (IOException) exception
				: new IOException("The persistence request failed.", exception);
	}

	private static String typeName(final Object object) {
		return object == null ? "null" : object.getClass().getName();
	}
}
