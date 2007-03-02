package net.hyperspacetravel.go3.server;

import java.net.*;
import java.io.*;
import java.util.Map;
import java.util.HashMap;

import net.hyperspacetravel.go3.ConnectedPlayer;
import net.hyperspacetravel.go3.ConnectionData;
import net.hyperspacetravel.go3.Game;
import net.hyperspacetravel.go3.GameBase;
import net.hyperspacetravel.go3.Player;
import net.hyperspacetravel.go3.Utility;

/**
 * TODO: multiconnections
 * 		 adjustable board size
 * 		 other settings
 * 
 * @author helge
 */
class GoGridServer extends GameBase {
	
	public GoGridServer () {
		Utility.setDebugMode (DEBUG);
	}
	
	/** set up server socket and start listening */
	public void start () {
		try {						
			serverSocket = new ServerSocket(serverPort);//  set up the server socket
		} catch (IOException e) {						//	TODO error handling
			Utility.bitch (new Throwable ("Could not listen on port: "+serverPort));
			System.exit(0);                             //  die of resource starvation
		}

		newGame();
	}
	
	/**  accept clients, and pass on to a Game object. create a GameThread from 
	 * this and start it.	 */ 
	void newGame () {	
				
		while (true) {
			
			//	wait for a client to connect
			Socket clientSocket = null;
			try {						
				clientSocket = serverSocket.accept();
			} catch (IOException e) {					//	TODO error handling
				Utility.bitch (new Throwable ("accept() failed:"+e.getMessage()));
				System.exit(0);                         //  die of resource starvation
			}
			
			ConnectedPlayer player = 
				new ConnectedPlayer (new Player (players.size()), clientSocket);

			if (players.size() >= SERVER_MAX_PLAYERS) {
				player.getOutStream().println("Maximum number of connections ("+
						SERVER_MAX_PLAYERS+") reached. Cannot serve you. Sorry.");
				try { clientSocket.close(); } catch (IOException e) { }
				continue;
			}

			if (!readUsername (player)) {
				try { clientSocket.close(); } catch (IOException e) { }
				continue;
			}
			
			//	TODO bad hack to determine the colour from the player ID
			player.setColour(player.getID()%2+1);	
			
			players.put (player.getUsername(), player);
			
			protocols.put(player, new ServerProtocol(this, player));
			protocols.get(player).start();
			
			Utility.debug(player.getUsername()+" connected from "
					+player.getClientSocket().getInetAddress().getHostAddress());
		}
		
	}
	
	void startGame(ConnectedPlayer player, String key) {
		games.put (key, new Game (player.getWantedBoardSize(), serverSocket));
		games.get (key).addPlayer(player);
	}

	void reconnect (ConnectedPlayer player) {
		assert precondition (false, "Implement me!");					//	TODO	
	}
	/**	read the player's name from in and set it in the Player object	or compare 
 	it if it's already set in the object */
	boolean readUsername (ConnectedPlayer player) {
		try {
			String username = player.getInStream().readLine ();
			//	check whether a player of name username is already connected
			if (players.containsKey(username)) {
				//	if so, reject this name EXCEPT if you have disconnected
				if (players.get(username).isConnected()) { 
					Utility.debug(username+" tried to connect, but is already connected!");
					player.getOutStream().println(		//	no Protocol exists yet, complain manually
					"go away, you're already connected!");
					player.getClientSocket().close();
					
					return false;				//	next connection attempt								
				}
			}
			//	if we reach  this point, we have a connected, accepted
			//	player with a defined username.
			player.setUsername(username);
			player.out.println("ok");			
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println (e.getMessage());
			return false;										//	try it again, although there's not much hope.
		}
		return true;
	}
	
		
	/**	 the <tt>ServerSocket</tt> on which we listen for connecting clients  */
	protected ServerSocket serverSocket = null;
		
	/**	 <tt>ConnectedPlayer</tt>s representing the participants	 		  */
	protected Map<String, ConnectedPlayer> players = new HashMap<String, ConnectedPlayer> ();
	
	/**	 store the <tt>ServerProtocol</tt>s associated with each <tt>ConnectedPlayer</tt>.<br>
	 *   This is a little awkward. maybe it would be better to add a <tt>ServerProtocol</tt>
	 *   field to <tt>ConnectedPlayer</tt>.<br>
	 *   Also, TODO: Usage of a <tt>Map</tt> is not yet safe. Add <tt>equals()</tt>
	 *   and <tt>hashcode()</tt> methods to <tt>ConnectedPlayer</tt>.
	 */
	protected Map<ConnectedPlayer, ServerProtocol> protocols = new HashMap<ConnectedPlayer, ServerProtocol>(); 

	/**	list of current <tt>Game</tt>s										  */
	protected Map<String, Game> games = new HashMap<String, Game> ();
	
	/** auxiliary object to exchange data with ConnectionDialog				  */
	private static ConnectionData connectionData = new ConnectionData ();

	protected static void help () {
		System.out.println ("usage: java GoGridServer" +
				" [-p|--server-port serverport]\n");
	}

	protected static void parse (String [] args) {
		
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-p") || args[i].equals("--server-port")) {
				try {
					connectionData.setServerPort(Integer.parseInt(args[++i]));
				} catch (NumberFormatException e) {
					help ();
				}
			}
			else help();
		}
	}

	/** print some information and marketing blah */
	protected static void banner () {
		System.out.println ("Three-dimensional Go server version "+VERSION);
		System.out.println ("written by "+MAINTAINER);
		System.out.println ();
	}
	
	/**
	 start a gogrid server
	 @param args the command line
	 */
	public static void main (String args[]) {

		banner ();
		parse (args);		
		
		GoGridServer server = new GoGridServer ();
		server.setServerPort(connectionData.getServerPort());
		server.start();
	}
}