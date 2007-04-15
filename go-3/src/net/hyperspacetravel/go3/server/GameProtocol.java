package net.hyperspacetravel.go3.server;

import net.hyperspacetravel.go3.Colour;
import net.hyperspacetravel.go3.ConnectedPlayer;
import net.hyperspacetravel.go3.FieldNotPresentException;
import net.hyperspacetravel.go3.Game;
import net.hyperspacetravel.go3.GoGrid;
import net.hyperspacetravel.go3.GoGridProtocol;
import net.hyperspacetravel.go3.OutsideGridException;
import net.hyperspacetravel.go3.Utility;


/**
 this class handles the input from the associated client and triggers the
 appropriate actions in the server
 */
public class GameProtocol extends GoGridProtocol {	
	public GameProtocol (ConnectedPlayer player, Game game) {
		super ();
		
		assert precondition (player.isConnected(), "Player must be connected!");
		assert precondition (game != null, "Game must exist!");
		
		connected = true;
		this.server = game;
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
		server.sendMessage (to, ""+player+": "+msg);        //  send 'msg' to 'to'
	}

	protected void logOff (String input) {
		error ("command not yet implemented: "+input);
	}

	protected void transmitBoard (String input) {
		assert precondition (gameStarted(), "Game must have started!");
		
		server.updateBoard (player);                    //  send board to player		
	}
	
	protected void cursor (String input) {
		assert precondition (gameStarted(), "Game must have started!");
		
		int x, y, z;
		try {
			x = Integer.parseInt (Utility.getArg (input, 2));
			y = Integer.parseInt (Utility.getArg (input, 3));
			z = Integer.parseInt (Utility.getArg (input, 4));
		} catch (NumberFormatException e) {
			Utility.warning (input);
			return;
		} catch (FieldNotPresentException e) { 
			Utility.warning (input);
			return;
		} catch (OutsideGridException e) {
			Utility.warning (input);
			return;
		}
		server.setCursor (player.getID(),  x, y, z);		
	}

	protected void activate (String input) {
		assert precondition (gameStarted(), "Game must have started!");

		nyi (input);
	}

	protected void deactivate (String input) {
		assert precondition (gameStarted(), "Game must have started!");

		nyi (input);
	}
	
	protected void liberties (String input) {
		assert precondition (gameStarted(), "Game must have started!");
		
		int x, y, z;
		try {
			x = Integer.parseInt (Utility.getArg (input, 2));
			y = Integer.parseInt (Utility.getArg (input, 3));
			z = Integer.parseInt (Utility.getArg (input, 4));
		} catch (NumberFormatException e) {
			Utility.warning (input);
			return;
		} catch (FieldNotPresentException e) {
			Utility.warning (input);
			return;
		} catch (OutsideGridException e) {
			Utility.warning (input);
			return;
		}
		int liberties = server.Liberty (x, y, z, player.getID(), false);
		
		out.println ("liberties "+liberties);
		
		//		error ("command not yet implemented: "+input);		
	}
	
	protected void saveGame (String input) {
		assert precondition (gameStarted(), "Game must have started!");
		
		error ("command not yet implemented: "+input);		
	}
	
	protected void setAt (String input) {
		assert precondition (gameStarted(), "Game must have started!");
//		assert precondition (awaitingMove(), "Must be on the move!");
		if (!awaitingMove()) return;
		
		int x, y, z;
		try {
			x = Integer.parseInt (Utility.getArg (input, 3));
			y = Integer.parseInt (Utility.getArg (input, 4));
			z = Integer.parseInt (Utility.getArg (input, 5));
		} catch (NumberFormatException e) {
			Utility.warning ("NumberFormatException in "+input);
			return;
		} catch (FieldNotPresentException e) {
			Utility.warning (input);
			return;
		} catch (OutsideGridException e) {
			Utility.warning (input);
			return;
		}
		
		Utility.debug ("    setting player "+player+" at ("+x+", "+y+", "+z+")");
		
		//  try setting at (x, y, z)
		boolean success = server.setStone (player.getID()+1, x, y, z);
		if (success) {		                //  on success:
			//			Utility.debug ("    ok");
			out.println ("ok");
			server.updateBoard ();                  //  send board to all players
			awaiting_move = false;		        //  toggle state to 'not ready'
			server.nextPlayer ();                   //  activate next player
		}
	}

	protected void pass (String input) {
		assert precondition (gameStarted(), "Game must have started!");
		assert precondition (awaitingMove(), "Must be on the move!");
		
		server.updateBoard (player);                //  send board to player
		awaiting_move = false;		        //  toggle state to 'not ready'
		server.nextPlayer ();                       //  activate next player
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
		if (s >= 3 && s <= GoGrid.MAX_GRID_SIZE /* && (s & 1) == 1 */ ) {
			server.setBoardSize (s);		        //  set board size
			server.sendMessage (-1,                     //  inform all players about new board size
					"BoardSize is now "+s+"x"+s+"x"+s);
		} 
		else {
			error ("Board size must lie between 3 and "+GoGrid.MAX_GRID_SIZE);
			Utility.warning (input);
		}
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
		if (c < server.getNumPlayers ()) {
//			if (server.setColor (player.toInt(), c)) {		//  set color, if not yet in use
			if (server.setColor (player)) {		//  set color, if not yet in use
				server.sendMessage (-1,		        //  inform all players about color change for player
						"Player "+player+" now has color "+c);
//				player = new Player (c);
			}
		}
		else {
			error ("Color must lie between 0 and "+(server.getNumPlayers ()-1)+" and not yet be used");
			Utility.warning (input);
		}
	}
	
	protected void setHandicap (String input) {
		assert precondition (false, "setHandicap() should not be used in this class!");
	}

	protected void gameList (String input) {
//		assert precondition (false, "joinGame() should not be used in this class!");
	}

	protected void joinGame (String input) {
		assert precondition (false, "joinGame() should not be used in this class!");
	}
	
	protected void setPlayers (String input) {
		assert precondition (false, "setPlayers() should not be used in this class!");
	}
	
	protected void loadGame (String input) {
		assert precondition (false, "loadGame() should not be used in this class!");
	}
	
	/** starts the game for all clients. requested explicitly by client. */
	protected void startGame (String input) {
		assert precondition (!gameStarted(), "Game must not have started yet!");

		//	TODO
		assert precondition (!gameStarted(), "Game must not have started yet!");
		Utility.debug(input);
		
		int s, c, h, p;
		try {
			s = Integer.parseInt (Utility.getArg (input, 3));
			c = Integer.parseInt (Utility.getArg (input, 4));
			h = Integer.parseInt (Utility.getArg (input, 5));
			p = Integer.parseInt (Utility.getArg (input, 6));
		} catch (NumberFormatException e) {
			Utility.warning (input);
			return;		    
		}
		player.setWantedBoardSize(s);
		player.setColour(c);
		player.setHandicap(h);
		player.setWantedNumPlayers(p);
		
// TODO	server.startGame (player, player.getUsername()+" waiting");
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
	
	protected void awaitMove () {
		assert precondition (gameStarted(), "Game must have started!");

		Utility.debug ("player "+player+" ready");
		game_started = true;
		awaiting_move = true;
		out.println ("ready");
	}
	
	protected void setColour (int col) { 
		assert precondition ((col >= Colour.BLACK && col <= Colour.WHITE), 
				"color must lie between "+Colour.name(Colour.BLACK)+" and "+Colour.name(Colour.WHITE));
		
		out.println ("set color "+col); }

	/** starts the game for the connected client. called by server.startGame() */
	protected void startGame () {
		assert precondition (!gameStarted(), "Game must not have started yet!");

		game_started = true;
		Utility.debug("started "+gameStarted()+" running "+gameRunning()+" move "+awaitingMove());
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
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	protected Game server = null;	

	protected boolean connected = false,
	await_clients = false;

	private String boardContent = "";

}

