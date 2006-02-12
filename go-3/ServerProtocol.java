
import java.io.*;
import java.net.*;

/**
 this class handles the input from the associated client and triggers the
 appropriate actions in the server
 */
class ServerProtocol extends GoGridProtocol {	
	ServerProtocol (GoGridServer server, ConnectedPlayer player) {
		super ();
		
		this.server = server;
		this.player = player;
		this.in = player.getInStream();
		this.out = player.getOutStream();
	}
	
	
	////////////////////////////////////////////////////////////////////////////
	//																		  //
	//			ACTIONS AS DEFINED IN THE PROTOCOL							  //
	//																		  //
	////////////////////////////////////////////////////////////////////////////
	
	protected void sendMessage (String input) {
		int to;
		String msg = Utility.getArgs (input, 3);
		try {
			to = Integer.parseInt (Utility.getArg (input, 2));
		} catch (NumberFormatException e) {
			Utility.warning (input);
			return;
		}
//		TODO: implement
	}

	protected void logOff (String input) {
		error ("command not yet implemented: "+input);
	}

	protected void transmitBoard (String input) {
		assert precondition (gameStarted(), "Game must have started!");
		
		nyi(input);		
	}
	
	protected void cursor (String input) {
		assert precondition (false, "cursor() should not be used in this class!");
	}

	protected void activate (String input) {
		assert precondition (false, "activate() should not be used in this class!");
	}

	protected void deactivate (String input) {
		assert precondition (false, "deactivate() should not be used in this class!");
	}
	
	protected void liberties (String input) {
		assert precondition (false, "liberties() should not be used in this class!");
	}
	
	protected void saveGame (String input) {
		assert precondition (false, "saveGame() should not be used in this class!");
	}
	
	protected void setAt (String input) {
		assert precondition (false, "setAt() should not be used in this class!");
	}

	protected void pass (String input) {
		assert precondition (false, "pass() should not be used in this class!");
	}

	protected void setBoardSize (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
		
		int s;
		try {
			s = Integer.parseInt (Utility.getArg (input, 4));
		} catch (NumberFormatException e) {
			Utility.warning (input);
			return;		    
		}
		player.setWantedBoardSize(s);
	}

	protected void setColour (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
		
		int c;
		try {
			c = Integer.parseInt (Utility.getArg (input, 3));
		} catch (NumberFormatException e) {
			Utility.warning (input);
			return;
		}
		player.setColour(c);
	}
	
	protected void setHandicap (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
		
		int h;
		try {
			h = Integer.parseInt (Utility.getArg (input, 3));
		} catch (NumberFormatException e) {
			Utility.warning (Utility.getArg (input, 3));
			return;		    
		}
		player.setHandicap (h);		//  set handicaps
	}
	
	protected void joinGame (String input) {
		//TODO
	}
	protected void setPlayers (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");

		int p;
		try {
			p = Integer.parseInt (Utility.getArg (input, 3));
		} catch (NumberFormatException e) {
			Utility.warning (input);
			return;		    
		}
		player.setWantedNumPlayers(p); 
	}
	
	protected void loadGame (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
		
		error ("command not yet implemented: "+input);
	}
	
	/** starts the game for all clients. requested explicitly by client. */
	protected void startGame (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");

		server.startGame (player, player.getUsername()+" waiting");
	}
	

	////////////////////////////////////////////////////////////////////////////
	//																		  //
	//			ACTIONS TO CALL FROM THE SERVER DIRECTLY					  //
	//																		  //
	////////////////////////////////////////////////////////////////////////////
	
	/** starts the game for the connected client. called by server.startGame() */
	protected void startGame () {
		assert precondition (!gameStarted(), "Game must not have started yet!");

		game_started = true;
		out.println ("start game");
	}

	protected void message (String m) {	out.println(m); }

	protected void error (String e) {
		Utility.warning (e);
		out.println(e);                         //  send e to player
	}

	
	////////////////////////////////////////////////////////////////////////////
	//																		  //
	//			CLASS-LOCAL HELPER FUNCTIONS								  //
	//																		  //
	////////////////////////////////////////////////////////////////////////////
	
	protected void lostConnection () {
		if (false) {								//	disabling bailout, just for kicks
			Utility.warning ("null input - player "+player+" disconnected!");
			Utility.warning ("exiting - cannot yet handle the reconnection of disconnected players.");
			Utility.warning ("sorry.");
		
			System.exit (0);
		}
		server.reconnect (player);		
	}
		
	protected boolean gameStarted () { return game_started; }

	protected boolean awaitingMove() { return awaiting_move; }
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	protected GoGridServer server = null;	

	protected boolean connected = false,
	await_clients = false;

	private String boardContent = "";

}

