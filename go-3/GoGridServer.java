
import java.net.*;
import java.io.*;

class GoGridServer extends GameBase /* extends GoGrid */ {
	
	public GoGridServer (int size) {

		setBoardSize (size);
		
		Utility.setDebugMode (true);

		newGame ();
	}
	
	
	/** set up server socket, accept one (1) client, and pass on to a Game
	 * object
	 */ 
	void newGame () {
		try {						
			serverSocket = new ServerSocket(serverPort);	//  set up the server socket
		} catch (IOException e) {
			Utility.bitch (new Throwable ("Could not listen on port: "+serverPort));
			System.exit(0);                                    //  die of resource starvation
		}
		
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
		
	
	/** print some information and marketing blah */
	static void banner () {
		System.out.println ("Three-dimensional Go server version "+VERSION);
		System.out.println ("written by "+MAINTAINER);
		System.out.println ();
		System.out.println ("Usage: ");
		System.out.println ("java GoGridServer [boardsize]");  	
	}
	
	/**
	 start a gogrid server
	 @param args the command line
	 command line: java GoGrid [gridsize]
	 MISSING: A BETTER PARSE FUNCTION, SETTING E.G. PORT, NUMBER OF PLAYERS,
	 WHATEVER, AND TAKING COMMAND LINE SWITCHES
	 */
	public static void main (String args[]) {
		int s;
		if (args.length == 0) s = 3;
		else s = Integer.parseInt(args[0]);
		
		banner ();
		
		System.out.println ("Board size is "+s+"x"+s+"x"+s);
		
		GoGridServer server = new GoGridServer (s);
	}
}