
import java.io.*;


/**
 this class handles the input from the associated client and triggers the
 appropriate actions in the server
 */
class GoGridProtocol extends Thread {
	
	protected boolean connected = false,
	await_clients = false,
	game_started = false,
	awaiting_move = false;
	
	protected ConnectedPlayer player = null;
	
	protected Game server = null;
	
	protected BufferedReader in = null;
	protected PrintWriter out = null;
	

	GoGridProtocol (ConnectedPlayer player, Game game) {
		connected = true;
		this.server = game;
		this.player = player;
		this.in = player.getInStream();
		this.out = player.getOutStream();
	}
	
	public void run () {
		while (true) {											//	outer loop to catch disconnects
			
			while (true) {										//	inner loop over moves
				String inputLine = null;
				
				try {
					inputLine = in.readLine ();
				} catch (IOException e) {
					Utility.bitch (new Throwable ("error reading line from socket!"));
					break;
				}
				if (inputLine == null) break;
				
				Utility.debug ("player "+player+":  "+inputLine);
				
				processInput (inputLine);
			}

			//	we're here because we've lost connection to the client
			
			if (false) {											//	disabling bailout, just for kicks
				Utility.warning ("null input - player "+player+" disconnected!");
				Utility.warning ("exiting - cannot yet handle the reconnection of disconnected players.");
				Utility.warning ("sorry.");
			
				System.exit (0);
			}
			server.connectWith (player);
		}
	}
	
	
	public void processInput (String input) {
		
		Utility.debug ("\""+input+"\"");
		//  requests which can be made at any time
		
		if (input.startsWith ("talk")) {
			int to;
			String msg = Utility.getArgs (input, 3);
			try {
				to = Integer.parseInt (Utility.getArg (input, 2));
			} catch (NumberFormatException e) {
				Utility.warning (input);
				return;
			}
			server.sendMessage (to, ""+player+": "+msg);        //  send 'msg' to 'to'
			return;
		}
		
		if (input.startsWith ("log off")) {
			error ("command not yet implemented: "+input);
			return;
		}
		
		
		//  requests which can be made only after game started
		
		if (gameStarted ()) {
			
			if (input.startsWith ("send board")) {
//				server.updateBoard (player.toInt());                    //  send board to player
				server.updateBoard (player);                    //  send board to player
				return;
			}
			
			if (input.startsWith ("cursor")) {
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
				return;
			}
			
			if (input.startsWith ("liberties")) {
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
				return;
			}
			
			if (input.startsWith ("save game")) {
				error ("command not yet implemented: "+input);
				return;
			}
			
			//  requests which can be made only if player is on move
			
			if (awaiting_move) {
				
				//  set a stone
				if (input.startsWith ("set at")) {
					//		    Utility.debug ("               "+input);
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
					else {                                      //  could not set:
						out.println ("error");
						//      send error message to player
						error ("("+x+", "+y+", "+z+"): Position occupied!");
					}
					return;
				}
				
				//  pass this move
				if (input.startsWith ("pass")) {
//					server.updateBoard (player.toInt());                //  send board to player
					server.updateBoard (player);                //  send board to player
					awaiting_move = false;		        //  toggle state to 'not ready'
					server.nextPlayer ();                       //  activate next player
					return;
				}
			}
			
			//  requests which can be made only if player is NOT on move
			
			else {
				//  dummy for requests a client may make when not on move
				if (input.startsWith ("")) {
					return;
				}
			}
		}                                       //  if (gameStarted ())
		
		//  requests which can be made only before game started
		
		else {
			
			//  request particular board size
			if (input.startsWith ("set board size")) {
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
				
				return;
			}
			
			//  request a color
			if (input.startsWith ("set color")) {
				int c;
				try {
					c = Integer.parseInt (Utility.getArg (input, 3));
				} catch (NumberFormatException e) {
					Utility.warning (input);
					return;
				}
				if (c < server.getNumPlayers ()) {
//					if (server.setColor (player.toInt(), c)) {		//  set color, if not yet in use
					if (server.setColor (player)) {		//  set color, if not yet in use
						server.sendMessage (-1,		        //  inform all players about color change for player
								"Player "+player+" now has color "+c);
//						player = new Player (c);
					}
				}
				else {
					error ("Color must lie between 0 and "+(server.getNumPlayers ()-1)+" and not yet be used");
					Utility.warning (input);
				}
				return;
			}
			
			//  request handicap
			if (input.startsWith ("set handicap")) {
				int h;
				try {
					h = Integer.parseInt (Utility.getArg (input, 3));
				} catch (NumberFormatException e) {
					Utility.warning (Utility.getArg (input, 3));
					return;		    
				}
				if (h >= 2 && h <= GoGrid.MAX_HANDICAPS) {
//					server.setHandicap (player.toInt(), h);		//  set handicaps
					server.setHandicap (player, h);		//  set handicaps
					server.sendMessage (-1,		        //  inform all players about handicap for player
							"Player "+player+" has handicap "+player.getHandicap());
					
				} 
				else {
					Utility.warning (input);
					error ("Number of handicaps must lie between 0 and "+GoGrid.MAX_HANDICAPS);
				}
				return;
			}
			
			//  request number of players
			if (input.startsWith ("set players")) {
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
				return;
			}
			
			//  load a game
			if (input.startsWith ("load game")) {
				error ("command not yet implemented: "+input);
				return;
			}
			
			// request game start
			if (input.startsWith ("start game")) {
				game_started = true;
				server.startGame ();
				return;
			}
			
		}
		
		Utility.warning ("command invalid: "+input);
	}
	
	protected void sendSize (int xsize, int ysize, int zsize) {
		out.println ("size "+xsize+" "+ysize+" "+zsize);
	}
	
	protected void startBoardTransmission () { boardContent = "stones "; }
	protected void transmitStone (int col, int x, int y, int z) {
		assert GameBase.precondition ((col >= 0 && col <= Colour.WHITE), 
				"color must lie between 0 and "+Colour.name(Colour.WHITE));

		boardContent += col+" "+x+" "+y+" "+z+" ";
	}
	protected void sendBoard () { out.println (boardContent); }
	private String boardContent = "";
	
	protected void ackUsername () {	out.println ("ok");	}
	
	protected void awaitMove () {
		Utility.debug ("player "+player+" ready");
		awaiting_move = true;
		out.println ("ready");		
	}
	
	protected void setColour (int col) { 
		assert GameBase.precondition ((col >= Colour.BLACK && col <= Colour.WHITE), 
				"color must lie between "+Colour.name(Colour.BLACK)+" and "+Colour.name(Colour.WHITE));
		
		out.println ("color "+col); }
	
	protected boolean gameStarted () { return game_started; }
	protected void startGame () {
		game_started = true;
		out.println ("start game");
	}
	
	protected void message (String m) {	out.println(m); }
	
	protected void error (String e) {
		Utility.warning (e);
		out.println(e);                         //  send e to player
	}
	
	
}

