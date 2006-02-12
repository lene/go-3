
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
//		server.sendMessage (to, ""+player+": "+msg);        //  send 'msg' to 'to'
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
		if (c <= server.getNumPlayers ()) {
			player.setColour(c);
		}
		else {
			error ("Color must lie between 0 and "+(server.getNumPlayers ()));
			Utility.warning (input);
		}
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
		if (h >= 2 && h <= GoGrid.MAX_HANDICAPS) {
			player.setHandicap (h);		//  set handicaps
		} 
		else {
			Utility.warning (input);
			error ("Number of handicaps must lie between 0 and "+GoGrid.MAX_HANDICAPS);
		}
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
		if (p >= 0 && p <= GoGrid.MAX_PLAYERS) {
			server.setNumPlayers (p);                   //  set num players
			server.sendMessage (-1,                     //  inform all players about new number of players
					"number of players in now "+server.getNumPlayers ()); 
		} 
		else {
			error ("Number of players must lie between 0 and "+GoGrid.MAX_PLAYERS);
			Utility.warning (input);
		}
	}
	
	protected void loadGame (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
		
		error ("command not yet implemented: "+input);
	}
	
	/** starts the game for all clients. requested explicitly by client. */
	protected void startGame (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");

		server.startGame ();
	}
	

	////////////////////////////////////////////////////////////////////////////
	//																		  //
	//			ACTIONS TO CALL FROM THE SERVER DIRECTLY					  //
	//																		  //
	////////////////////////////////////////////////////////////////////////////
	
	protected void sendSize (int xsize, int ysize, int zsize) {
		out.println ("size "+xsize+" "+ysize+" "+zsize);
	}
	
	protected void startBoardTransmission () { 
		boardContent = "stones "; }
	protected void transmitStone (int col, int x, int y, int z) {
		assert precondition ((col >= 0 && col <= Colour.WHITE), 
				"color must lie between 0 and "+Colour.name(Colour.WHITE));

		boardContent += col+" "+x+" "+y+" "+z+" ";
	}
	protected void sendBoard () { 
		out.println (boardContent); }
	
	protected void ackUsername () {	
		assert precondition (!gameStarted(), "Game must not yet have started!");
		out.println ("ok");	}
		
	protected void setColour (int col) { 
		assert precondition ((col >= Colour.BLACK && col <= Colour.WHITE), 
				"color must lie between "+Colour.name(Colour.BLACK)+" and "+Colour.name(Colour.WHITE));
		
		out.println ("color "+col); }

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
		server.connectWith (player);		
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

