package fr.tp.inf112.projects.robotsim.net;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class PersistenceClient {

	private static final int CONNECT_TIMEOUT_MILLIS = 5000;

	private static final int READ_TIMEOUT_MILLIS = 10000;

	private final String host;

	private final int port;

	public PersistenceClient(final String host, final int port) {
		if (host == null || host.isBlank()) {
			throw new IllegalArgumentException("Server host cannot be empty.");
		}
		if (port < 1 || port > 65535) {
			throw new IllegalArgumentException("Port must be between 1 and 65535.");
		}

		this.host = host;
		this.port = port;
	}

	public Object send(final Object request)
	throws IOException {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
			socket.setSoTimeout(READ_TIMEOUT_MILLIS);

			try (ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
				output.flush();
				output.writeObject(request);
				output.flush();

				try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
					final Object response = input.readObject();

					if (response instanceof IOException) {
						throw (IOException) response;
					}
					if (response instanceof Throwable) {
						throw new IOException("The persistence server rejected the request.",
								(Throwable) response);
					}

					return response;
				}
			}
		}
		catch (final ClassNotFoundException ex) {
			throw new IOException("Could not deserialize the persistence server response.", ex);
		}
	}
}
