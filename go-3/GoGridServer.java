
import java.net.*;
import java.io.*;
import java.util.Map;
import java.util.HashMap;

/**
 * TODO: multiconnections
 * 		 adjustable board size
 * 		 other settings
 * 
 * @author helge
 */
class GoGridServer extends GameBase {
	
	public GoGridServer (int size) {
		setBoardSize (size);		
		Utility.setDebugMode (true);
	}
	
	/** set up server socket and start listening */
	public void start () {
		try {						
			serverSocket = new ServerSocket(serverPort);	//  set up the server socket
		} catch (IOException e) {
			Utility.bitch (new Throwable ("Could not listen on port: "+serverPort));
			System.exit(0);                                 //  die of resource starvation
		}

		newGame();
	}
	
	/**  accept one (1) client, and pass on to a Game object	 */ 
	void newGame () {	
		//	wait for a client to connect
		Socket clientSocket = null;
		try {						
			clientSocket = serverSocket.accept();
		} catch (IOException e) {
			Utility.bitch (new Throwable ("accept() failed:"+e.getMessage()));
			System.exit(0);                                    //  die of resource starvation
		}
		
		ConnectedPlayer player = new ConnectedPlayer (new Player (players.size()), clientSocket);
		if (!readUsername (player)) return;
		
		//	TODO bad hack to determine the colour from the player ID
		player.setColour(player.getID()%2+1);	
		
		players.put (player.getUsername(), player);
		
		Utility.debug(player.getUsername()+" connected from "
				+player.getClientSocket().getInetAddress().getHostAddress());

		games.put ("first game", 
				new Game (getBoardSize(), player, serverSocket, clientSocket));
	
		if (false) {
			games.get ("first game").start();
		} 
		else {
			try {						
				clientSocket = serverSocket.accept();
			} catch (IOException e) {
				Utility.bitch (new Throwable ("accept() failed:"+e.getMessage()));
				System.exit(0);                                    //  die of resource starvation
			}
			
			player = new ConnectedPlayer (new Player (players.size()), clientSocket);
			if (!readUsername (player)) return;
			
			//	TODO bad hack to determine the colour from the player ID
			player.setColour(player.getID()%2+1);	
			
			players.put (player.getUsername(), player);
			
			Utility.debug(player.getUsername()+" connected from "
					+player.getClientSocket().getInetAddress().getHostAddress());
			
			games.get ("first game").addPlayer(player);
			
			games.get ("first game").startGame();
		}
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

	/**	list of current <tt>Game</tt>s										  */
	protected Map<String, Game> games = new HashMap<String, Game> ();
	
	/** auxiliary object to exchange data with ConnectionDialog				  */
	private static ConnectionData connectionData = new ConnectionData ();

	protected static void help () {
		System.out.println ("usage: java GridDisplay [-s|--board-size boardsize]\n"+
	      "                   [-p|--server-port serverport]\n");
	}

	protected static void parse (String [] args) {
		
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-s") || args[i].equals("--board-size")) {
				try {
					connectionData.setBoardSize(Integer.parseInt(args[++i]));
				} catch (NumberFormatException e) {
					help ();
				}
			}
			else if (args[i].equals("-p") || args[i].equals("--server-port")) {
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
		
		int s = connectionData.getBoardSize();
		
		System.out.println ("Board size is "+s+"x"+s+"x"+s);
		
		GoGridServer server = new GoGridServer (s);
		server.setServerPort(connectionData.getServerPort());
		server.start();
	}
}