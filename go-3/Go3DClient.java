import java.awt.Frame;
import java.net.*;
import java.io.*;

import com.sun.j3d.utils.applet.MainFrame;

public class Go3DClient {
	
	static protected ConnectedPlayer setupConnection () {

		ConnectedPlayer player = null;
		try {
			Socket clientSocket = new Socket(connectionData.getServerHost(), 
					connectionData.getServerPort());
			player = new ConnectedPlayer (
					new Player (0),	clientSocket);
		} catch (IOException e) {
			Utility.warning ("GoGridClient.setupConnection (): Connect to "+
					connectionData.getServerHost()+" on port "+
					connectionData.getServerPort()+" failed");
			System.exit(0);					//	TODO decent error handling
		}
		try {
			player.out.println(connectionData.getUsername());
			String ack = player.in.readLine();
			System.out.println (ack);
			if (!ack.equals("ok")) {
				Utility.debug(ack);
				System.exit (0);			//	TODO decent error handling
			}
		} catch (IOException e) { 			//	TODO decent error handling
		} catch (NullPointerException e) {	//	TODO decent error handling
		}

		return player;
	}

	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          STATIC SECTION STARTS                                         //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	

	/**	 number of moves to fake	 */
	static int m = 0;

	private static ConnectionData connectionData = new ConnectionData ();

	/**
	 print a message on how to call the program
	 */
	protected static void help () {
		System.out.println ("usage: java GridDisplay [-s|--board-size boardsize]\n"+
				"                   [-m|--auto-moves automoves]\n"+
				"                   [-u|--user-id username]\n"+
		    "                   [-h|--server-host serverhost]\n"+
	      "                   [-p|--server-port serverport]\n");
	}
		
	/**
	 parse the command line and set appropriate options
	 */
	protected static void parse (String [] args) {
		
		try {
			InetAddress addr = InetAddress.getLocalHost();
			connectionData.setUsername(System.getProperty ("user.name")+"@"+addr.getHostName());
    } catch (UnknownHostException e) {
    	connectionData.setUsername(System.getProperty ("user.name"));
    }    
  
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-s") || args[i].equals("--board-size")) {
				try {
					connectionData.setBoardSize(Integer.parseInt(args[++i]));
				} catch (NumberFormatException e) {
					help ();
				}
			}
			if (args[i].equals("-m") || args[i].equals("--auto-moves")) {
				try {
					m = Integer.parseInt(args[++i]);
				} catch (NumberFormatException e) {
					help ();
				}
			}
			if (args[i].equals("-p") || args[i].equals("--server-port")) {
				try {
					connectionData.setServerPort(Integer.parseInt(args[++i]));
				} catch (NumberFormatException e) {
					help ();
				}
			}
			if (args[i].equals("-h") || args[i].equals("--server-host")) {
				connectionData.setServerHost(args[++i]);
			}
			if (args[i].equals("-u") || args[i].equals("--user-id")) {
				connectionData.setUsername(args[++i]);
			}
		}
	}
	
	
	/**
	 main method; allows this class to be run as an application as well as an
	 applet
	 */
	public static void main (String[] args) {
		parse (args);
	
		ConnectionDialog conn = new ConnectionDialog (connectionData);
		conn.setVisible(true);
		
		Utility.debug("Board size  = "+connectionData.getBoardSize());
		Utility.debug("Pre-moves   = "+m);
		Utility.debug("Server port = "+connectionData.getServerPort());
		Utility.debug("Server host = "+connectionData.getServerHost());
		Utility.debug("Username    = "+connectionData.getUsername());

		GridDisplay game = null;
		if (!connectionData.getStartGame()) {
			ConnectedPlayer p = setupConnection();

			ChooseGameDialog choose = new ChooseGameDialog(p, connectionData);
			choose.setVisible(true);
			
			Utility.debug("Chosen game = "+connectionData.getGame());

			game = new GridDisplay (connectionData, p);
		}
		else {
			game = new GridDisplay (connectionData);
		}
		Frame frame = new MainFrame (game, 600, 600);

		if (false) {
			for (int i = 0; i < m; i++) {
				game.fakeGame (2, Colour.BLACK);
				game.repaint ();
			}
		}
		
	}

}