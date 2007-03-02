import java.io.BufferedReader;
import java.io.PrintWriter;
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
	
	ClientProtocol (GoGridClient client, BufferedReader in, PrintWriter out) {
		super ();
		
		assert precondition(client != null, "Client must exist");
		assert precondition(in != null, "Instream must exist");
		assert precondition(out != null, "Outstream must exist");
		
		this.client = client;
		this.in = in;
		this.out = out;
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
		nyi(input);
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
//		assert precondition (!gameStarted(), "Game must not have started yet!");

		//	TODO decouple client code from protocol syntax
		/*if (!gameStarted())*/	client.setSize (input);
	}

	protected void setColour (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");

		int col = -1;
		try {
			col = Integer.parseInt (Utility.getArg (input, 3));
		} catch (NumberFormatException e) {
			Utility.debug ("NumberFormatException: "+e.getMessage()+input);
		}
		this.client.setCurrentPlayer(col);

		assert postcondition (this.client.getCurrentPlayer() == col, 
				"setColour: should be "+col+", is "+this.client.getCurrentPlayer());
	}
	
	protected void setHandicap (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
		nyi(input);
	}
	
	protected void gameList (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
		nyi(input);
	}

	protected void joinGame (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
		assert precondition (in != null, "in == null");
		assert precondition (out != null, "out == null");

		Utility.debug("join game "+input);
		out.println("join game "+input);
		while (true) {
			String response = null;
			try { response = in.readLine(); }
			catch (IOException e) { 
				Utility.debug(e.getMessage());
				stop(true);
				return;
			}
			if(response.startsWith("size")) {
				try { 
					this.client.setBoardSize(Integer.parseInt (Utility.getArg (response, 2))); 
					return;
				} 
				catch (NumberFormatException e) { 
					Utility.debug ("NumberFormatException: "+e.getMessage()+input); 
					continue;
				}
			}
			else {
				Utility.bitch(new Throwable ("rejected: "+response));
				stop(true);
				System.exit(0);
			}
		}
	}

	protected void setPlayers (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");
		nyi(input);
	}
	
	protected void loadGame (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");		
		nyi(input);
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
	
	protected void startGame(int boardSize, int color, int handicaps, int numPlayers) {
		assert precondition (in != null, "in == null");
		assert precondition (out != null, "out == null");
		System.out.println("start game "+boardSize+" "+color+" "+handicaps+" "+numPlayers);
		out.println("start game "+boardSize+" "+color+" "+handicaps+" "+numPlayers);
	}
	
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