
import java.io.*;
import java.util.LinkedList;

/**  redeclaring the class Stack to avoid using old java.util.Stack */
class Stack extends LinkedList<String> { 
	public void push(String s) { addFirst (s); }
}

/**
 this class handles the input from the associated client and triggers the
 appropriate actions in the server
 */
abstract class GoGridProtocol extends Thread {	

	GoGridProtocol () {
	}
	
	/** the event loop: reads a line from server and handles it, forever.	  */
	final public void run () {
		
		while (true) {								//	outer loop to catch disconnects
			
			while (true) {
				
				String inputLine = new String ();
				
				try {
					inputLine = in.readLine ();
				} catch (IOException e) {
					Utility.bitch (new Throwable ("error reading line from socket!"));
					break;
				}
				if (inputLine == null) break;
				
				Utility.debug ("player "+player+":  "+inputLine);
				
				messages.push(inputLine);
				
				processInput (inputLine);
			}
			//	we're here because we've lost connection to the client
			lostConnection();
		}
	}

	/** parse a line of input and call the corresponding action.			  */
	final void processInput (String input) {
		
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
		//	transmit board to client
		if (input.startsWith ("transmit board")) {			//	TODO: replace
			transmitBoard (input);	return;
		}
		//	alternative syntax
		if (input.startsWith ("stones")) {					//	TODO: replace
			transmitBoard (input);	return;
		}
		//	set cursor 
		if (input.startsWith ("cursor")) {
			cursor (input);		return;
		}
		//	set setting state
		if (input.startsWith ("ready")) {					//	TODO: implement
			activate (input);	return;
		}
		//	set setting state
		if (input.startsWith ("ok")) {						//	TODO: implement
			deactivate (input);	return;
		}
		//	error message
		if (input.startsWith ("error")) {					//	TODO: implement
			error (input);	return;
		}
		
		//  requests which can be made only before game started
		
		if (!gameStarted ()) {
			
			//	get list of current games
			if (input.startsWith ("game list")) {			//	TODO: implement
				nyi(input); 			return;
			}
			//	request join game
			if (input.startsWith ("join game")) {			//	TODO: implement
				joinGame(input);		return;
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
			//	alternative syntax
			if (input.startsWith ("size")) {				//	TODO: replace
				setBoardSize (input);	return;
			}
			//  request a color
			if (input.startsWith ("set color")) {			//	TODO: replace
				setColour (input);		return;
			}

		}                                       		//  if (!gameStarted ())
		
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

	abstract protected void transmitBoard (String input);
	
	abstract protected void cursor (String input);
	
	abstract protected void activate (String input);

	abstract protected void deactivate (String input);

	abstract protected void liberties (String input);
	
	abstract protected void saveGame (String input);
	
	abstract protected void setAt (String input);

	abstract protected void pass (String input);

	abstract protected void setBoardSize (String input);

	abstract protected void setColour (String input);
	
	abstract protected void joinGame (String input);

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
	
	protected void sendSize (int xsize, int ysize, int zsize) { }

	protected void startBoardTransmission () {
		Utility.bitch(new Throwable ("This method may only be used in the server Protocol!"));
		System.exit(0);
	}
	protected void transmitStone (int col, int x, int y, int z) {
		Utility.bitch(new Throwable ("This method may only be used in the server Protocol!"));
		System.exit(0);
	}
	protected void sendBoard () { 
		Utility.bitch(new Throwable ("This method may only be used in the server Protocol!"));
		System.exit(0);
	}

	protected void ackUsername () {	
		assert precondition (!gameStarted(), "Game must not yet have started!");
	}
	
	protected void awaitMove () {
		assert precondition (gameStarted(), "Game must have started!");
	}
	
	protected void setColour (int col) { 
		assert precondition ((col >= Colour.BLACK && col <= Colour.WHITE), 
				"color must lie between "+Colour.name(Colour.BLACK)+" and "+Colour.name(Colour.WHITE));
	}

	/** starts the game for the connected client. called by server.startGame() */
	protected void startGame () {
		assert precondition (!gameStarted(), "Game must not have started yet!");
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
	
	//	convenience functions making the GameBase functions accessible from this
	//	class's descendants (which are NOT derived from GameBase)
	final static boolean precondition (boolean test, String message) {
		return GameBase.precondition(test, message);
	}
	final static boolean postcondition (boolean test, String message) {
		return GameBase.postcondition(test, message);
	}

	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	protected ConnectedPlayer player = null;
	protected BufferedReader in = null;
	protected PrintWriter out = null;

	protected boolean game_started = false,
					  awaiting_move = false;
	protected boolean gameStarted () { return game_started; }
	protected boolean gameRunning () { return gameStarted(); }	//	TODO: implement
	protected boolean awaitingMove() { return awaiting_move; }

	
	/** sort of an anally retentive inclination of mine to keep all messages  */
	protected final Stack messages = new Stack();
	String lastMessage () { return messages.peek(); }
}
