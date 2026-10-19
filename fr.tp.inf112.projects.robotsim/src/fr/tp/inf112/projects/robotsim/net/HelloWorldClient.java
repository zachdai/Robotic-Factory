package fr.tp.inf112.projects.robotsim.net;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HelloWorldClient {

	private static final String DEFAULT_HOST = "localhost";

	private static final String DEFAULT_MESSAGE = "Hello World";

	private static final Logger LOGGER = Logger.getLogger(HelloWorldClient.class.getName());

	private HelloWorldClient() {}

	public static void main(final String[] args) {
		final String host = args.length > 0 ? args[0] : DEFAULT_HOST;
		final int port;

		try {
			port = args.length > 1 ? parsePort(args[1]) : HelloWorldServer.DEFAULT_PORT;
		}
		catch (final Exception ex) {
			LOGGER.log(Level.SEVERE, "Invalid server port.", ex);
			return;
		}

		final String message = args.length > 2
				? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
				: DEFAULT_MESSAGE;

		try (Socket socket = new Socket(host, port);
			 ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
			output.flush();

			try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
				output.writeObject(message);
				output.flush();

				final Object response = input.readObject();

				if (!(response instanceof String)) {
					throw new IOException("Expected a String response but received "
							+ typeName(response) + ".");
				}

				LOGGER.info(() -> "Server response: " + response);
			}
		}
		catch (final Exception ex) {
			LOGGER.log(Level.SEVERE, "Could not communicate with the Hello World server.", ex);
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
