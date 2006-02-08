
import java.net.*;
import java.io.*;
import java.util.Vector;
import java.util.HashMap;

/**
 * A Game consists of 2 (or generally <tt>numPlayers</tt>) Players, who can dis-
 * and reconnect to the same Game. Thus a Game has a unique GameID.
 * A Game is spawned by the GoGridServer upon request by a Client for a new 
 * Game. Thereafter it is in a pending state, until another Client connects and
 * is accepted.
 *  
 * @author helge
 */
class Game extends GoGrid {
	
	public Game (int size, Player player, ServerSocket serverSocket, Socket clientSocket) {
		super (size);
		this.serverSocket = serverSocket;
		
		Utility.setDebugMode (true);
		
		setupBoard ();                  //  initialize board structure
		
		reserveContainers ();           //  get storage space for client data
		
		initPlayer (player, clientSocket);
		
		waitForConnections ();
		
		startGame ();
	}
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          OVERRIDDEN METHODS START                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	
	/**
	 starts the game, i.e. makes setting possible
	 */
	void startGame () {
		Utility.debug ("GoGridServer.startGame (): "+numPlayers+" Players");
		for (int i = 0; i < players.size(); i++) {
			ConnectedPlayer player = players.elementAt(i);
			updateBoard (player);
			player.getProtocol().startGame ();
		}
		nextPlayer ();                  //  currentPlayer initialized to -1 => start with player 0
	}
	
	
	/**
	 switches to next player
	 */
	void nextPlayer () {
		currentPlayer = (currentPlayer+1)%numPlayers;
		ConnectedPlayer player = players.elementAt(currentPlayer);
		Utility.debug ("current player is now "+player);
		player.getProtocol().awaitMove ();
		player.getOutStream().println ("ready");
	}
	
	
	/**
	 sets a stone of color <tt>currentPlayer</tt>at the cursor position<br>
	 if that position is already occupied, or some other error occurs, does
	 nothing<br>
	 */
	boolean setStone () {
		Utility.bitch (new Throwable ("This function does not make sense. or does it?"));
		return super.setStone ();
	}
	
	/**
	 sets a stone of given player at the given position<br>
	 if that position is already occupied, returns false<br>
	 clears any stones captured by the move<br>
	 @param p Player to set
	 @param x x position to set
	 @param y y position to set
	 @param z z position to set
	 @return success
	 */
	boolean setStone (Player p, int x, int y, int z) {
		Utility.bitch(new Throwable ("setStone (Player, ...) not yet " +
		"implemented - add Colour property to Player class first"));
		System.exit(0);
		return false;
	}
	
	/**
	 sets a stone of given color at the given position<br>
	 if that position is already occupied, returns false<br>
	 clears any stones captured by the move<br>
	 @param col color to set
	 @param x x position to set
	 @param y y position to set
	 @param z z position to set
	 @return success
	 ADD EXCEPTION HANDLERS (ARRAY INDEX OUT OF BOUNDS, ...?)
	 */
	boolean setStone (int col, int x, int y, int z) {
		if (x < 1 || x > getBoardSize () ||
				y < 1 || y > getBoardSize () ||
				z < 1 || z > getBoardSize ())
			return false;
		
		//  TO DO: check for ko's
		if (stones[x][y][z] == Colour.EMPTY) {	//  able to set?	
			stones[x][y][z] = col;				//  fill board position	
			xset = x; yset = y; zset = z;		//  remember last set position
			
			checkArea ();					//  check for captives
//			moveBuffer.add (new Move (x, y, z, col));		//  remember this move
//			Utility.debug (moveBuffer.size ()+": ("+x+", "+y+", "+z+"), "
//			+Liberty (x, y, z, col, false)+" liberties");
			//	    printGrid ();
			return true;					//  success
		}
		else {
			Utility.debug ("Position occupied: ("+x+", "+y+", "+z+")");
			return false;					// failure
		}
	}	    
	
	/**
	 sends the whole board to a specified player
	 @param player the player who made the request
	 */
	void updateBoard (ConnectedPlayer p) {
		p.getOutStream().println ("size "+getBoardSize (0)
				+" "+getBoardSize (1)
				+" "+getBoardSize (2));
		String content = "stones ";
		for (int x = 1; x <= getBoardSize (0); x++)
			for (int y = 1; y <= getBoardSize (1); y++)
				for (int z = 1; z <= getBoardSize (2); z++)
					content += getStone (x, y, z)+" "+x+" "+y+" "+z+" ";
		
		Utility.debug ("Player "+p/*+": "+content*/);
		p.getOutStream().println (content);
	}
	
	/**
	 sends the whole board to all players
	 */
	void updateBoard () {
		for (int i = 0; i < players.size(); i++) {
			ConnectedPlayer player = players.elementAt(i);
			updateBoard (player);
		}
	}

	/** 
	 * reimplemented because it is abstract in the base class; makes no sense  
	 * with all players represented by a Player (or ConnectedPlayer) object.
	 * should be removed soon in the base class. 
	 */	
	void sendMessage (int i, String message) { 
		sendMessage (players.elementAt (i), message);
	}

	/**
	 send a text message to one player
	 @param player the addressee
	 @param message the message to be sent
	 */
	void sendMessage (ConnectedPlayer player, String message) {
		player.getOutStream().println ("message "+message);
	}
	
	
	/**
	 return (a generally wrong, i.e. much too high, value for) the liberties
	 which are left to the stone at the given position<br>
	 @param x x position to check
	 @param y y position to check
	 @param z z position to check
	 @param current player who checks this position
	 @param shortCut if true, returns after the first free neighbor is found
	 (well, approximately - i think there is a bug concerning
	 this)<br>
	 */
	int Liberty (int x, int y, int z, 
			int Current, boolean ShortCut) {
		int S = stones[x][y][z];                         	//  save  current  color
		
		int liberty = 0;                                	//  start  counting
		//  check neighbours in x-direction:
		for (int xx = x-1; xx <= x+1; xx += 2) {        	//  check  both  next  places
			if (stones[xx][y][z] == Colour.EMPTY) {		//  neighbor  place  free ?
				//  don't need to check  for grid boundary here,  cause  it's set to  OCCUPIED
				if (ShortCut) {                                 //  being  lazy ?
					stones[x][y][z] = S;                        //  any  liberty  at  all,
					return 1;                                   //  we  know  enough
				}
				else liberty++;                                 //  add  one,  to  count  all
			}
			else if (stones[xx][y][z] == Current) {          	//  next place  =  own color?
				stones[x][y][z] = Colour.OCCUPIED;              //  mark the current position
				liberty += Liberty (xx, y, z, Current, ShortCut);	//  add its liberties
			}
		}
		
		//  check neighbours in y-direction
		for (int yy = y-1; yy <= y+1; yy += 2) {        	//  same procedure
			if (stones[x][yy][z] == Colour.EMPTY) {             //  CAN  I  PUT  THESE PIECES
				if (ShortCut) {                                 //  TOGETHER  INTO  ONE ?
					stones[x][y][z] = S;                         
					return 1; }                                    
				else liberty++; }              
			else if (stones[x][yy][z] == Current) {
				stones[x][y][z] = Colour.OCCUPIED;              
				liberty += Liberty (x, yy, z, Current, ShortCut); } }
		
		//  check neighbours in z-direction                  	//  gets  a  bit  boring ...
		for (int zz = z-1; zz <= z+1; zz += 2) {
			if (stones[x][y][zz] == Colour.EMPTY) {                 
				if (ShortCut) {
					stones[x][y][z] = S;                         
					return 1; }                                    
				else liberty++; }              
			
			else if (stones[x][y][zz] == Current) {
				stones[x][y][z] = Colour.OCCUPIED;              
				liberty += Liberty (x, y, zz, Current, ShortCut); } }
		
		stones[x][y][z] = S;                               	//  reset  grid  place       
		return liberty;
	}    
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          OVERRIDDEN METHODS END                                        //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////

	/**
	 construct a ConnectedPlayer as first player and add it to the player list
	 @param player a preexisting simple Player object
	 @param clientSocket a socket from which the player attempts a connection
	 @return the completely initialized ConnectedPlayer
	 */
	ConnectedPlayer initPlayer (Player player, Socket clientSocket) {
		ConnectedPlayer cp = new ConnectedPlayer (player, clientSocket);
		String username = null;
		try {
			username = cp.getInStream().readLine ();

			cp.setUsername (username);
			cp.getOutStream().println ("ok");

			cp.setColour(Colour.BLACK);

			setColor (cp);
			updateBoard (cp);
			cp.setProtocol(new GoGridProtocol (cp, this));
			cp.getProtocol().start ();
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println (e.getMessage());
			System.exit(0);
		}
		Utility.debug("player "+username+" connected from "+
				clientSocket.getInetAddress().getHostName());
		
		players.addElement (cp);
		return players.lastElement();
	}
	
	/**
	 set up 1 (one) client connection:
	 <ul>
	 <li>client socket
	 <li>output stream to client
	 <li>input stream from client
	 <li>user name
	 <li>tell the client its color
	 <li>send board size and board to client
	 <li>start a thread to handle communication with the client
	 </ul>
	 @param player the client with whom to (re-)connect
	 */
	void connectWith (Player player) {
		Utility.debug ("waiting for player "+player.getID()
				+(!player.getUsername().equals("")? 
						" ["+player.getUsername ()+"]": 
						"")+
		" to connect");
		
		ConnectedPlayer activePlayer;
		
		connect:
			while (true) {					//	loop until connection succeeds
				try {
					//  create a client socket with accept() and insert it into the
					//  socket map
					Socket clientSocket = serverSocket.accept();
					
					activePlayer = new ConnectedPlayer (player, clientSocket);
					
					//	read the player's name from in and set it in the Player object
					//	or compare it if it's already set in the object
					try {
						String username = activePlayer.getInStream().readLine ();
						if (player.getUsername().equals("")) {	//	new player connecting, player not yet set
							//	check whether a player of name username is already connected	[TODO]
							for (int i = 0; i < players.size(); i++) {
								//	if so, reject this name:
								//	EXCEPT if you are the player who has disconnected	[TODO]
								if (players.elementAt(i).getUsername().equals (username)) {
									Utility.debug(username+" tried to connect, but is already connected!");
									activePlayer.getOutStream().println(
											"message go away, you're already connected!");
									activePlayer.getClientSocket().close();

									continue connect;		//	next connection attempt								
								}
							}
							//	if not, we can continue:
							activePlayer.setUsername(username);
							activePlayer.setColour(player.getID());	//	the number of the initial Player object denotes its colour

							players.addElement (activePlayer);
						}
						//	player reconnects after a disconnection
						else if (username.equals(player.toString())) { 
							//	replace entry in player list with active player
							activePlayer.setUsername(username);
							for (int i = 0; i < players.size(); i++) {
								if (players.elementAt(i).getUsername().equals (username)) {
									players.setElementAt(activePlayer, i);
									break;
								}
							}
						}
						//	we wait for a specific player to reconnect, but 
						//	someone else connects
						else {
							Utility.debug(username+" tried to connect, but we want user "+
									player+" to connect!");
							activePlayer.getOutStream().println(
									"message go away, we want user "+player+" to connect!");
							activePlayer.getClientSocket().close();
							
							continue connect;		//	next connection attempt
						}
						
						//	if we reach  this point, we have a connected, accepted
						//	player with a defined username.
						Utility.debug(activePlayer.getUsername()+" connected from "
								+activePlayer.getClientSocket().getInetAddress().getHostAddress());
						//	acknowledge player name
						activePlayer.getOutStream().println ("ok");
					} catch (IOException e) {
						e.printStackTrace();
						System.err.println (e.getMessage());
						continue connect;										//	try it again, although there's not much hope.
					}
					
					//  tell the client its color
					setColor (activePlayer);
					
					//  send board size and board to client
					updateBoard (activePlayer);
					
					//  create a thread to handle communications with the client 
					//  and add it to the thread list
					activePlayer.setProtocol(
							new GoGridProtocol (activePlayer, this));
					
					//  start the created thread
					activePlayer.getProtocol().start ();
					
				} catch (IOException e) {
					Utility.bitch (new Throwable ("Accept failed: "+serverPort));
					e.printStackTrace ();
					System.err.println (e.getMessage());
					continue connect;											//	try it again, although there's not much hope.
				}
				catch (ArrayIndexOutOfBoundsException e) {
					Utility.bitch (new Throwable ("Couldn't create client socket: Player number"+
							player.getID()+"out of range"));
					e.printStackTrace ();
					System.err.println (e.getMessage());
					continue connect;											//	try it again, although there's not much hope.
				}
				
				if (currentPlayer >= 0) {
					activePlayer.getProtocol().startGame ();
					updateBoard (activePlayer);
					if (player.getID() == currentPlayer)
						activePlayer.getOutStream().println ("ready");
				}
				
				Utility.debug ("Client "+(player.getID()+1)+" connected from "+
						activePlayer.getClientSocket().getInetAddress ().getHostAddress());
				
				return;
			}
	}
	
	/**
	 tries to change the color of a player 
	 @param p the player
	 */
	boolean setColor (ConnectedPlayer p) {
		p.getOutStream().println ("color "+p.getColour());
		return true;
	}
	
	/**
	 set the handicap of a player
	 @param player the player making the request
	 @param handicap wanted handicap; will be normalized taking the handicaps
	 of all other players into account
	 */
	void setHandicap (Player player, int h) {
		if (h <= MAX_HANDICAPS && h >= 0) 
			player.setHandicap(h);
		
		//  find out whether smallest handicap of all players is > 0
		int hmin = MAX_HANDICAPS+1;
		for (int i = 0; i < players.size(); i++) {
			Player p = players.elementAt (i);
			if (p.getHandicap() < hmin)	hmin = p.getHandicap();
		}
		
		//  if so, subtract its value from all handicaps, so that handicaps
		//  start at 0 again
		//	TODO this is not safe for numPlayers > 2, as the handicaps may be
		//	renormalized REPEATEDLY, leading to too small values
		for (int i = 0; i < players.size(); i++) {
			Player p = players.elementAt (i);
			p.setHandicap(p.getHandicap()-hmin);
		}
	}
		
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          PROTECTED SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	
	/**
	 reserve all <tt>java.util.Vector</tt>s to have a size of
	 <tt>numPlayers</tt>
	 */
	protected void reserveContainers () {
		players.ensureCapacity (numPlayers);
	}	
	
	/**
	 wait for further players to connect.
	 block until all connections are made<br>
	 set up client connections:
	 <ul>
	 <li>client sockets
	 <li>output streams to client
	 <li>input streams from client
	 <li>tell the client its color
	 <li>send board size and board to client
	 <li>start a thread to handle communication with the client
	 </ul>
	 */
	protected void waitForConnections () {
		Utility.debug ("waiting for "+(numPlayers-1)+" clients to connect...");
		
		for (int i = 1; i < numPlayers; i++) 
			connectWith (new Player (i));
	}
		
	/**
	 check the whole grid for captives<br>
	 clear any captives found<br>
	 THERE SHOULD BE A MORE DESCRIPTIVE NAME FOR THIS FUNCTION<br>
	 */
	protected void checkArea () {
		for (int x = xset-1; x <= xset+1; x ++)			//  check neighboring stones
			for (int y = yset-1; y <= yset+1; y ++)		//  on the grid
				for (int z = zset-1; z <= zset+1; z ++)          
					if (((x != xset) || (y != yset) || (z != zset))//  the   stone  set  now ...
							&& (stones[x][y][z] != Colour.EMPTY))
						checkArea (x, y, z);
		
		checkArea (xset, yset, zset);				//  ... must  be checked last
	}
	
	/** 
	 check whether the stone at the given position has been captured<br>
	 if so, clear it and all connected stones<br>
	 @param x x position to check
	 @param y y position to check
	 @param z z position to check<br>
	 THERE SHOULD BE A MORE DESCRIPTIVE NAME FOR THIS FUNCTION<br>
	 */
	protected void checkArea (int x, int y, int z) {
		int P = stones[x][y][z];
		if (P != Colour.EMPTY && P != Colour.OCCUPIED)
			if (Liberty (x, y, z, P, true) == 0)            //  check liberties
				clearArea (x, y, z, P);                     //  remove, if zero
	}
	
	/** 
	 clear the stone at the given position and all stones connected to it<br>
	 check whether the given position really belongs to the requested player<br>
	 @param x x position to clear
	 @param y y position to clear
	 @param z z position to clear
	 @param current player who owns this position
	 */
	protected void clearArea (int x, int y, int z, int current) {
		if (stones[x][y][z] != current) return;            	//  nothing  to  clear
		
		Utility.debug ("cleared ("+x+", "+y+", "+z+")");
		
		stones[x][y][z] = Colour.EMPTY;                     //  clear  this  point
		
		//  ISSUE: does this need to be set in the move buffer?
		//  MoveBuffer[++MoveNbr] = Entry (xset, yset, zset, stones[xset][yset][zset]); 
		
		for (int xx = x-1; xx <= x+1; xx += 2)          	//  check  neighbors :
			if (getStone (xx, y, z) == current &&           //  same  color ?
					xx >= 1 && xx <= getBoardSize (0))      //  on  grid ?
				clearArea (xx, y, z, current);              //  then  clear !
		
		for (int yy = y-1; yy <= y+1; yy += 2)          	//  again...
			if (getStone (x, yy, z) == current &&           //  same  color ?
					yy >= 1 && yy <= getBoardSize (1))      //  on  grid ?
				clearArea (x, yy, z, current); 
		
		for (int zz = z-1; zz <= z+1; zz += 2)          	//  and again
			if (getStone (x, y, zz) == current &&           //  same  color ?
					zz >= 1 && zz <= getBoardSize (2))      //  on  grid ?
				clearArea (x, y, zz, current);
	}
	
	/**
	 send a string message to all connected clients
	 @param msg the message to be sent
	 */
	protected void broadcast (String msg) {
		try {
			for (int i = 0; i < players.size (); i++)
				players.elementAt (i).getOutStream().println (msg);
		}
		catch (ArrayIndexOutOfBoundsException e) { }
		catch (NullPointerException e) { }
	}
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	
	/**
	 sets the board size and allocates the memory for the board<br>
	 tells all connected clients about the new board size
	 @param s board size
	 */       
	void setBoardSize (int s) {					
		super.setBoardSize (s);
		broadcast ("set size "+s);
	}
	
	/**
	 coordinates of the stone last set
	 */
	protected int xset, yset, zset;
	
	//  the client data are stored in <tt>Vector</tt>s, so they can be easily
	//  dynamically stored, even when the number of players changes
	
	/**
	 the <tt>ServerSocket</tt> on which we listen for connecting clients
	 */
	protected ServerSocket serverSocket = null;
	
	/**
	 <tt>ConnectedPlayer</tt>s representing the participant - 
	 */
	protected Vector<ConnectedPlayer> players = new Vector<ConnectedPlayer> ();
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION ENDS                                        //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
		
}