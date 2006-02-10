
import java.io.*;


/**
 this class handles the input from the associated client and triggers the
 appropriate actions in the server
 */
abstract class GoGridProtocol extends Thread {	

	GoGridProtocol () {
	}
	
	/** the event loop: reads a line from server and handles it, forever.	  */
	abstract public void run ();

	/** parse a line of input and call the corresponding action.			  */
	/* final */ void processInput (String input) {
		
		Utility.debug ("\""+input+"\"");

		//	requests to be made before the user is authenticated do not go here,
		//	because the Protocol object is not yet established then.
		
		//  requests which can be made at any time
		
		if (input.startsWith ("send message")) {
			sendMessage(input);		return;
		}
		if (input.startsWith ("broadcast message")) {
			nyi(input);				return;
		}
		if (input.startsWith ("log off")) {
			logOff(input); 			return;
		}
		
		//  requests which can be made only before game started
		
		if (!gameStarted ()) {
			
			//	get list of current games
			if (input.startsWith ("game list")) {			//	TODO: implement
				nyi(input); 			return;
			}
			//	request join game
			if (input.startsWith ("join game")) {			//	TODO: implement
				nyi (input);			return;
			}
			//  request handicap
			if (input.startsWith ("set handicap")) {
				setHandicap (input);	return;
			}
			//  request number of players
			if (input.startsWith ("set players")) {			//	TODO: implement
				setPlayers(input);		return;
			}
			//	get list of current games
			if (input.startsWith ("saved game list")) {		//	TODO: implement
				nyi(input); 			return;
			}
			//	load game
			if (input.startsWith ("load game")) {			//	TODO: implement
				loadGame(input);		return;
			}
			// request game start
			if (input.startsWith ("start game")) {
				startGame(input);		return;
			}
			//  request particular board size
			if (input.startsWith ("set board size")) {		//	TODO: replace
				setBoardSize(input);	return;	
			}
			//  request a color
			if (input.startsWith ("set color")) {			//	TODO: replace
				setColour (input);		return;
			}
			
		}                                       //  if (!gameStarted ())
		
		//  requests which can be made only after game started

		else {
			if (!gameRunning()) {
				//  accept a request to join
				if (input.startsWith ("accept join")) {		//	TODO: implement
					nyi (input);		return;
				}
				//  reject a request to join
				if (input.startsWith ("reject join")) {		//	TODO: implement
					nyi (input);		return;
				}
			}
			else {
				//	transmit board to client
				if (input.startsWith ("send board")) {		//	TODO: replace
					sendBoard (input);	return;
				}
				//	set cursor 
				if (input.startsWith ("cursor")) {
					cursor (input);		return;
				}
				//	set setting state
				if (input.startsWith ("ready")) {			//	TODO: implement
					nyi (input);		return;
				}
				//	request/send liberties
				if (input.startsWith ("liberties")) {
					liberties (input);	return;
				}
				if (input.startsWith ("save game")) {		//	TODO: implement
					saveGame(input);	return;
				}
				if (input.startsWith ("take back")) {		//	TODO: implement
					nyi(input);		return;
				}
				if (input.startsWith ("accept take back")) {//	TODO: implement
					nyi(input);		return;
				}
				if (input.startsWith ("reject take back")) {//	TODO: implement
					nyi(input);		return;
				}
				if (input.startsWith ("goto branch")) {		//	TODO: implement
					nyi(input);		return;
				}
				if (input.startsWith ("accept goto branch")) {// TODO: implement
					nyi(input);		return;
				}
				if (input.startsWith ("reject goto branch")) {// TODO: implement
					nyi(input);		return;
				}
				
				//  requests which can be made only if player is on move
				
				if (awaitingMove()) {
					
					//  set a stone
					if (input.startsWith ("set at")) {
						setAt (input);		return;
					}
					//  pass this move
					if (input.startsWith ("pass")) {
						pass (input);		return;
					}
				}
				
				//  requests which can be made only if player is NOT on move
				
				else {
					//  stub for requests a client may make when not on move
					if (input.startsWith ("")) {
						nyi(input);			return;
					}
				}
			}
			
		}
		
		Utility.warning ("command invalid: "+input);
	}

	
	////////////////////////////////////////////////////////////////////////////
	//																		  //
	//			ACTIONS AS DEFINED IN THE PROTOCOL							  //
	//																		  //
	////////////////////////////////////////////////////////////////////////////
	
	abstract protected void sendMessage (String input);

	abstract protected void logOff (String input);

	abstract protected void sendBoard (String input);
	
	abstract protected void cursor (String input);
	
	abstract protected void liberties (String input);
	
	abstract protected void saveGame (String input);
	
	abstract protected void setAt (String input);

	abstract protected void pass (String input);

	abstract protected void setBoardSize (String input);

	abstract protected void setColour (String input);
	
	abstract protected void setHandicap (String input);
	
	abstract protected void setPlayers (String input);
	
	abstract protected void loadGame (String input);
	
	/** starts the game for all clients. requested explicitly by client. */
	abstract protected void startGame (String input);
	
	
	final protected void nyi (String input) {
		Utility.bitch(new Throwable ("command not yet implemented: "+input));
	}
	
	////////////////////////////////////////////////////////////////////////////
	//																		  //
	//			ACTIONS TO CALL FROM THE SERVER/CLIENT DIRECTLY				  //
	//																		  //
	////////////////////////////////////////////////////////////////////////////
	
	protected void sendSize (int xsize, int ysize, int zsize) {
		out.println ("size "+xsize+" "+ysize+" "+zsize);
	}
	
	protected void startBoardTransmission () { 
		boardContent = "stones "; }
	protected void transmitStone (int col, int x, int y, int z) {
		assert GameBase.precondition ((col >= 0 && col <= Colour.WHITE), 
				"color must lie between 0 and "+Colour.name(Colour.WHITE));

		boardContent += col+" "+x+" "+y+" "+z+" ";
	}
	protected void sendBoard () { 
		out.println (boardContent); }
	
	protected void ackUsername () {	
		assert GameBase.precondition (!gameStarted(), "Game must not yet have started!");
		out.println ("ok");	}
	
	protected void awaitMove () {
		assert GameBase.precondition (gameStarted(), "Game must have started!");

		Utility.debug ("player "+player+" ready");
		awaiting_move = true;
		out.println ("ready");		
	}
	
	protected void setColour (int col) { 
		assert GameBase.precondition ((col >= Colour.BLACK && col <= Colour.WHITE), 
				"color must lie between "+Colour.name(Colour.BLACK)+" and "+Colour.name(Colour.WHITE));
		
		out.println ("color "+col); }

	/** starts the game for the connected client. called by server.startGame() */
	protected void startGame () {
		assert GameBase.precondition (!gameStarted(), "Game must not have started yet!");

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
	
	abstract protected void lostConnection ();
	
	protected boolean gameStarted () { return game_started; }

	protected boolean gameRunning () { return gameStarted(); }	//	TODO: implement

	protected boolean awaitingMove() { return awaiting_move; }
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	protected ConnectedPlayer player = null;
	protected BufferedReader in = null;
	protected PrintWriter out = null;

	protected boolean connected = false,
	await_clients = false,
	game_started = false,
	awaiting_move = false;

	private String boardContent = "";
}
