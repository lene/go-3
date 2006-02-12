import java.io.BufferedReader;
import java.io.IOException;

/**
 client side implementation of the GoGridProtocol.
 
 the protocol specified:
 <ol>
 <li>connect to the server
 <li>read your color
 <li>in no particular order:
 <ul>
 <li>read grid size and grid
 <li>read number of players
 <li>?
 </ul>
 <li>now the client is ready to:
 <ul>
 <li>send messages to any and all other clients
 <li>request change of board size
 <li>request change in number of players
 <li>request change of own handicaps
 <li>request change of color
 <li>request start of game
 </ul>
 <li>after starting the game, the client may:
 <ul>
 <li>request an update of the grid
 </ul>
 <li>when on the move, it may additionally
 <ul>
 <li>set a stone
 <li>pass
 </ul>
 </ol>
 
 TODO decouple the ClientProtocol from the GoGridClient, so that the client 
 doesn't need to know the Protocol syntax.
 TODO redesign and extend the protocol. 
 */

class ClientProtocol extends GoGridProtocol {
	
	ClientProtocol (GoGridClient client, BufferedReader in) {
		super ();
		
		assert precondition(client != null, "Client must exist");
		assert precondition(in != null, "Instream must exist");
		
		this.client = client;
		this.in = in;
	}

	
	////////////////////////////////////////////////////////////////////////////
	//																		  //
	//			ACTIONS AS DEFINED IN THE PROTOCOL							  //
	//																		  //
	////////////////////////////////////////////////////////////////////////////

	protected void sendMessage (String input) {
		client.message (Utility.getArgs (input, 2));
	}
	
	protected void logOff (String input) {
		nyi (input);
	}

	protected void transmitBoard (String input) {
//		client.updateBoard (input);
//		if (true) return;
		
		assert precondition (input.startsWith ("stones"),
				"bad board description line: "+input);
				
		Utility.debug (input);
		
		for (int i = 0; 
			 i < client.getBoardSize (0)*client.getBoardSize (1)*client.getBoardSize (2); i++) {
			
			try {
				int color = Integer.parseInt (Utility.getArg (input, 2+i*4)),
				xread = Integer.parseInt (Utility.getArg (input, 3+i*4)),
				yread = Integer.parseInt (Utility.getArg (input, 4+i*4)),
				zread = Integer.parseInt (Utility.getArg (input, 5+i*4));
				
				client.setStoneDirectly(color, xread, yread, zread);
			} catch (NumberFormatException e) {
				Utility.debug ("NumberFormatException: "+e.getMessage()+input);
			}
			catch (ArrayIndexOutOfBoundsException e) {
				Utility.debug ("ArrayIndexOutOfBoundsException");
			}
		}
		
		client.repaint();
		Utility.debug ("done");
}
	
	protected void cursor (String input) {
		assert precondition (gameStarted(), "Game must have started!");
	}

	protected void activate (String input) {
		assert precondition (gameStarted(), "Game must have started!");

		client.activate ();
	}

	protected void deactivate (String input) {
		assert precondition (gameStarted(), "Game must have started!");

		client.deactivate ();
		defineStatus (true);
	}

	protected void liberties (String input) {
		assert precondition (gameStarted(), "Game must have started!");

		try {
			liberties = Integer.parseInt (Utility.getArg (input, 2));
		} catch (NumberFormatException e) {
			Utility.debug ("NumberFormatException: "+input);
			liberties = -1;
		} 
	}
	
	protected void saveGame (String input) {
		assert precondition (gameStarted(), "Game must have started!");
	}
	
	protected void setAt (String input) {
		assert precondition (gameStarted(), "Game must have started!");
		assert precondition (awaitingMove(), "Must be on the move!");
	}

	protected void pass (String input) {
		assert precondition (gameStarted(), "Game must have started!");
		assert precondition (awaitingMove(), "Must be on the move!");
	}

	protected void setBoardSize (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");

		//	TODO decouple client code from protocol syntax
		client.setSize (input);
	}

	protected void setColour (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
	}
	
	protected void setHandicap (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
	}
	
	protected void setPlayers (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
	}
	
	protected void loadGame (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");		
		error ("command not yet implemented: "+input);
	}
	
	/** starts the game for all clients. requested explicitly by client. */
	protected void startGame (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
		client.startGame ();
		game_started = true;
	}
	
	protected void error (String e) {
		super.error(e);
		defineStatus (false);
	}


	////////////////////////////////////////////////////////////////////////////
	
	/** used to exchange data with the GoGridClient, which reads it */
	protected int liberties = -1;
	int liberties () { 
		int l = liberties;
		liberties = -1;		//	marks "read" state, not yet re-set by the server
		return l; }
	void liberties (int l) { liberties = l; }
	
	protected boolean status = false;
	boolean status () { return status; }
	
	protected boolean statusDefined = false;
	boolean statusDefined () { return statusDefined; }
	void undefineStatus () { statusDefined = false; }    
	private void defineStatus (boolean s) {
		statusDefined = true;
		status = s;
	}
	
	protected void lostConnection () {
		Utility.warning ("null input - server terminated!");
		// exit cleanly
		System.exit (0);
	}
	
	GoGridClient client;
	
}