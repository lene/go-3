
import java.io.*;


/**
 this class handles the input from the associated client and triggers the
 appropriate actions in the server
 */
abstract class GoGridProtocol extends Thread {	

	GoGridProtocol (/* ConnectedPlayer player, Game game */) {
/*		assert GameBase.precondition (player.isConnected(), "Player must be connected!");
		
		connected = true;
		this.player = player;
		this.in = player.getInStream();
		this.out = player.getOutStream();
*/
	}
	
	/** the event loop: reads a line from server and handles it, forever.	  */
	abstract public void run ();

	/** parse a line from the server and call the corresponding action.		  */
	abstract void processInput (String input);

	
	////////////////////////////////////////////////////////////////////////////
	//																		  //
	//			ACTIONS AS DEFINED IN THE PROTOCOL							  //
	//																		  //
	////////////////////////////////////////////////////////////////////////////
	
	protected void talk (String input) {
	}

	protected void logoff (String input) {
		error ("command not yet implemented: "+input);
	}

	protected void sendBoard (String input) {
		assert GameBase.precondition (gameStarted(), "Game must have started!");
	}
	
	protected void cursor (String input) {
		assert GameBase.precondition (gameStarted(), "Game must have started!");
	}
	
	protected void liberties (String input) {
		assert GameBase.precondition (gameStarted(), "Game must have started!");
	}
	
	protected void saveGame (String input) {
		assert GameBase.precondition (gameStarted(), "Game must have started!");
	}
	
	protected void setAt (String input) {
		assert GameBase.precondition (gameStarted(), "Game must have started!");
		assert GameBase.precondition (awaitingMove(), "Must be on the move!");
	}

	protected void pass (String input) {
		assert GameBase.precondition (gameStarted(), "Game must have started!");
		assert GameBase.precondition (awaitingMove(), "Must be on the move!");
	}

	protected void setBoardSize (String input) {
		assert GameBase.precondition (!gameStarted(), "Game must not have started yet!");
	}

	protected void setColour (String input) {
		assert GameBase.precondition (!gameStarted(), "Game must not have started yet!");
	}
	
	protected void setHandicap (String input) {
		assert GameBase.precondition (!gameStarted(), "Game must not have started yet!");
	}
	
	protected void setPlayers (String input) {
		assert GameBase.precondition (!gameStarted(), "Game must not have started yet!");
	}
	
	protected void loadGame (String input) {
		assert GameBase.precondition (!gameStarted(), "Game must not have started yet!");		
		error ("command not yet implemented: "+input);
	}
	
	/** starts the game for all clients. requested explicitly by client. */
	protected void startGame (String input) {
		assert GameBase.precondition (!gameStarted(), "Game must not have started yet!");
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

