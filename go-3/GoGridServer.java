
import java.net.*;
import java.io.*;

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
	
	public void start () {
		newGame();
	}
	
	/** set up server socket, accept one (1) client, and pass on to a Game
	 * object
	 */ 
	void newGame () {
		try {						
			serverSocket = new ServerSocket(serverPort);	//  set up the server socket
		} catch (IOException e) {
			Utility.bitch (new Throwable ("Could not listen on port: "+serverPort));
			System.exit(0);                                 //  die of resource starvation
		}
		
		//	wait for a client to connect
		Socket clientSocket = null;
		try {						
			clientSocket = serverSocket.accept();
		} catch (IOException e) {
			Utility.bitch (new Throwable ("accept() failed:"+e.getMessage()));
			System.exit(0);                                    //  die of resource starvation
		}
	
		Game game = new Game (getBoardSize(), new Player (0), serverSocket, clientSocket);		
	}
	
		
	/**
	 the <tt>ServerSocket</tt> on which we listen for connecting clients
	 */
	protected ServerSocket serverSocket = null;
		
	
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