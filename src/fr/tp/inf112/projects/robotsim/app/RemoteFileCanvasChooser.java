package fr.tp.inf112.projects.robotsim.app;

import java.io.IOException;

import javax.swing.JOptionPane;

import fr.tp.inf112.projects.canvas.view.FileCanvasChooser;
import fr.tp.inf112.projects.robotsim.net.PersistenceClient;
import fr.tp.inf112.projects.robotsim.net.PersistenceCommand;
import fr.tp.inf112.projects.robotsim.net.PersistenceProtocol;

public class RemoteFileCanvasChooser extends FileCanvasChooser {

	private final PersistenceClient client;

	public RemoteFileCanvasChooser(final String host, final int port) {
		super(PersistenceProtocol.FACTORY_FILE_EXTENSION, "Puck Factory");
		client = new PersistenceClient(host, port);
	}

	@Override
	protected String browseCanvases(final boolean open)
	throws IOException {
		if (!open) {
			final String canvasId = JOptionPane.showInputDialog(
					getViewer(),
					"Enter the factory file name:",
					"Save Factory",
					JOptionPane.QUESTION_MESSAGE);

			return canvasId == null ? null : canvasId.trim();
		}

		final Object response = client.send(PersistenceCommand.LIST_FILES);

		if (!(response instanceof String[])) {
			throw new IOException("Expected a factory file list but received "
					+ typeName(response) + ".");
		}

		final String[] canvasIds = (String[]) response;

		if (canvasIds.length == 0) {
			JOptionPane.showMessageDialog(
					getViewer(),
					"No factory file is available on the persistence server.",
					"Open Factory",
					JOptionPane.INFORMATION_MESSAGE);
			return null;
		}

		final Object selectedCanvas = JOptionPane.showInputDialog(
				getViewer(),
				"Select the factory to open:",
				"Open Factory",
				JOptionPane.QUESTION_MESSAGE,
				null,
				canvasIds,
				canvasIds[0]);

		return selectedCanvas == null ? null : selectedCanvas.toString();
	}

	private static String typeName(final Object object) {
		return object == null ? "null" : object.getClass().getName();
	}
}
