package fr.tp.inf112.projects.robotsim.net;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HelloWorldServer {

	public static final int DEFAULT_PORT = 5050;

	private static final Logger LOGGER = Logger.getLogger(HelloWorldServer.class.getName());

	private HelloWorldServer() {
	}

	public static void main(final String[] args) {
		final int port;

		try {
			port = args.length == 0 ? DEFAULT_PORT : parsePort(args[0]);
		}
		catch (Exception ex) {
			LOGGER.log(Level.SEVERE, "Invalid server port.", ex);
			return;
		}

		try (ServerSocket serverSocket = new ServerSocket(port)) {
			LOGGER.info(() -> "Hello World server listening on port " + port + ".");

			while (!Thread.currentThread().isInterrupted()) {
				try {
					handleClient(serverSocket.accept());
				}
				catch (final Exception ex) {
					LOGGER.log(Level.WARNING, "Could not process a client request.", ex);
				}
			}
		}
		catch (final Exception ex) {
			LOGGER.log(Level.SEVERE, "Could not run the Hello World server.", ex);
		}
	}

	private static void handleClient(final Socket clientSocket)
	throws IOException, ClassNotFoundException {
		try (Socket socket = clientSocket;
			 ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
			output.flush();

			try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
				final Object request = input.readObject();

				if (!(request instanceof String)) {
					throw new IOException("Expected a String request but received "
							+ typeName(request) + ".");
				}

				final String message = (String)request;
				final String response = "I received " + message + "!";
				LOGGER.info(() -> "I received " + message + "!");

				output.writeObject(response);
				output.flush();
			}
		}
	}

	private static String typeName(final Object object) {
		return object == null ? "null" : object.getClass().getName();
	}

	private static int parsePort(final String value) {
		final int port = Integer.parseInt(value);

		if (port < 1 || port > 65535) {
			throw new IllegalArgumentException("Port must be between 1 and 65535.");
		}

		return port;
	}
}
