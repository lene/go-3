package net.hyperspacetravel.go3.client.gui;

import java.awt.Frame;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.swing.JOptionPane;

import net.hyperspacetravel.go3.Colour;
import net.hyperspacetravel.go3.ConnectedPlayer;
import net.hyperspacetravel.go3.ConnectionData;
import net.hyperspacetravel.go3.GameBase;
import net.hyperspacetravel.go3.Player;
import net.hyperspacetravel.go3.Utility;

import com.sun.j3d.utils.applet.MainFrame;

class CantConnectException extends IllegalArgumentException {
	private static final long serialVersionUID = 5949335483927136880L;
}

class AlreadyConnectedException extends IllegalArgumentException {
	private static final long serialVersionUID = 5949335483927136880L;
}

public class Go3DClient {

	static protected ConnectedPlayer setupConnection() {

		ConnectedPlayer player = null;
		try {
			Socket clientSocket = new Socket(connectionData.getServerHost(),
					GameBase.getServerPort());
			player = new ConnectedPlayer(new Player(0), clientSocket);
		} catch (IOException e) {
			Utility.warning("GoGridClient.setupConnection (): Connect to "
					+ connectionData.getServerHost() + " on port "
					+ GameBase.getServerPort() + " failed");
			throw new CantConnectException();
		}
		try {
			player.out.println(connectionData.getUsername());
			String ack = player.in.readLine();
			Utility.debug("Server answer to connection request: " + ack);
			if (!ack.equals("ok")) {
				Utility.debug("Bad answer: " + ack);
				throw new AlreadyConnectedException();
			}
		} catch (IOException e) { // TODO decent error handling
		} catch (NullPointerException e) { // TODO decent error handling
		}

		return player;
	}

	// //////////////////////////////////////////////////////////////////////////
	// //
	// STATIC SECTION STARTS //
	// //
	// //////////////////////////////////////////////////////////////////////////

	/** number of moves to fake */
	static int m = 0;

	private static ConnectionData connectionData = new ConnectionData();

	/**
	 * print a message on how to call the program
	 */
	protected static void help() {
		System.out
				.println("usage: java GridDisplay [-s|--board-size boardsize]\n"
						+ "                   [-m|--auto-moves automoves]\n"
						+ "                   [-u|--user-id username]\n"
						+ "                   [-h|--server-host serverhost]\n"
						+ "                   [-p|--server-port serverport]\n"
						+ "                   [--debug]\n");
	}

	/**
	 * parse the command line and set appropriate options
	 */
	protected static void parse(String[] args) {

		try {
			InetAddress addr = InetAddress.getLocalHost();
			connectionData.setUsername(System.getProperty("user.name") + "@"
					+ addr.getHostName());
		} catch (UnknownHostException e) {
			connectionData.setUsername(System.getProperty("user.name"));
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-s") || args[i].equals("--board-size")) {
				try {
					connectionData.setBoardSize(Integer.parseInt(args[++i]));
				} catch (NumberFormatException e) {
					help();
				}
			}
			if (args[i].equals("-m") || args[i].equals("--auto-moves")) {
				try {
					m = Integer.parseInt(args[++i]);
				} catch (NumberFormatException e) {
					help();
				}
			}
			if (args[i].equals("-p") || args[i].equals("--server-port")) {
				try {
					GameBase.setServerPort(Integer.parseInt(args[++i]));
				} catch (NumberFormatException e) {
					help();
				}
			}
			if (args[i].equals("-h") || args[i].equals("--server-host")) {
				connectionData.setServerHost(args[++i]);
			}
			if (args[i].equals("-u") || args[i].equals("--user-id")) {
				connectionData.setUsername(args[++i]);
			}
			if (args[i].equals("--debug")) {
				Utility.setDebugMode(true);
			}
		}
	}

	/**
	 * main method; allows this class to be run as an application as well as an
	 * applet
	 */
	public static void main(String[] args) {
		parse(args);

		GridDisplay game = null;
		ChatWindow chatWindow = null;

		while (game == null) {
			try {
				ConnectionDialog conn = new ConnectionDialog(connectionData);
				conn.setVisible(true);

				Utility.debug("Pre-moves   = " + m);
				Utility.debug("Server port = " + GameBase.getServerPort());
				Utility
						.debug("Server host = "
								+ connectionData.getServerHost());
				Utility.debug("Username    = " + connectionData.getUsername());

				ConnectedPlayer p = setupConnection();

				if (Utility.getDebugMode()) {
					if (chatWindow == null) chatWindow = new ChatWindow();
					chatWindow.setVisible(true);
				}
				
				@SuppressWarnings("unused")
				ChooseGameDialog choose = new ChooseGameDialog(p, connectionData);

				while (connectionData.getGame() == "" && connectionData.getStartGame() == false) {
					Utility.sleep(100);
				}
				game = new GridDisplay(connectionData, p);
			} catch (CantConnectException e) {
				JOptionPane
						.showMessageDialog(
								null,
								"There is no Go³ server listening on host \""
										+ connectionData.getServerHost()
										+ "\" on port "
										+ GameBase.getServerPort()
										+ ".\n"
										+ "Please check your connection settings and try again.");
			} catch (AlreadyConnectedException e) {
				JOptionPane.showMessageDialog(null, "A user called \""
						+ connectionData.getUsername()
						+ "\" is already connected.\n"
						+ "Each user can have only one connection currently.\n"
						+ "Please choose a different user name.");
			} catch (UnsatisfiedLinkError e) {
				JOptionPane
						.showMessageDialog(
								null,
								"An unrecoverable Error occured: "
										+ e.getMessage()
										+ "\n\n"
										+ "Apparently you haven't set up the Java3D extension correctly.\n"
										+ "You must supply the path to the j3dcore-ogl library on the \n"
										+ "command line like this:\n\n"
										+ "    java -Djava.library.path=${J3D_LIBPATH} -cp ${CLASSPATH}\n"
										+ "         net.hyperspacetravel.go3.client.gui.Go3DClient\n\n"
										+ "On Gentoo Linux, for example, you can use:\n\n"
										+ "    java -Djava.library.path=$(java-config -i sun-java3d-bin)\n"
										+ "         -cp $(java-config -p sun-java3d-bin)\n"
										+ "         net.hyperspacetravel.go3.client.gui.Go3DClient\n\n"
										+ "I cannot guess the correct path on my own. I'll have to terminate.\n"
										+ "Sorry.");
				System.exit(1);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(null,
						"An unrecoverable Error occured: " + e.getMessage()
								+ "\n" + "I have to terminate. Sorry.");
				System.exit(1);
			}
		}

		// JDesktopPane frame = new JDesktopPane();
		Frame frame = new MainFrame(game, 600, 600);

		game.addCursorListener(new CursorDialog(game, frame));
		if (Utility.getDebugMode())
			game.addTransformListener(new NavigationDialog(game, frame));
		if (Utility.getDebugMode())
			game.getGrid().addChatListener(chatWindow);
		
		if (false) {
			for (int i = 0; i < m; i++) {
				game.fakeGame(2, Colour.BLACK);
				game.repaint();
			}
		}

	}

}
