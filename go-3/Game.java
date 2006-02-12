
import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.ListIterator;

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
	
	/** A Game is constructed from a known board size, a <tt>Player</tt> who has
	 * just connected from a <tt>ServerSocket</tt> and the <tt>Socket</tt> which
	 * has been created by the <tt>ServerSocket</tt> during <tt>accept ()</tt>.
	 * 
	 * @param size
	 * @param player 
	 * @param serverSocket
	 * @param clientSocket
	 */
	public Game(int size, ServerSocket serverSocket) {
		super (size);
		
		Utility.setDebugMode (true);

		this.serverSocket = serverSocket;

		setupBoard ();                  //  initialize board structure
	}
	
	public Game (int size, ConnectedPlayer player, ServerSocket serverSocket) {
		
		super (size);

		assert precondition ((size >= MIN_GRID_SIZE && size <= MAX_GRID_SIZE), 
				"Board size ["+size+"] must lie between "+MIN_GRID_SIZE+" and "+MAX_GRID_SIZE);
		assert precondition ((player.getID() >= 0 && player.getID() < MAX_PLAYERS), 
				"Player ID ["+player.getID()+"] must be between 0 and "+MAX_PLAYERS);
		assert precondition ((serverSocket != null), 
				"Server Socket must not be null, or nothing makes sense anymore");

		Utility.setDebugMode (true);
		
		this.serverSocket = serverSocket;
				
		setupBoard ();                  //  initialize board structure
	
		addPlayer(player);
	}
	
	void start() {
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
		assert precondition (players.size() == numPlayers,
				"There must be exactly "+numPlayers+" players connected.");
		assert precondition (currentPlayer == -1, 
				"currentPlayer ["+currentPlayer+"] must be initialized to -1");
		
		Utility.debug ("GoGridServer.startGame (): "+numPlayers+" Players");
		
		ListIterator<ConnectedPlayer> i = players.listIterator();
		while (i.hasNext()) {
			ConnectedPlayer player = i.next();
			updateBoard (player);
			player.getProtocol().startGame ();
		}
		nextPlayer ();                  //  currentPlayer initialized to -1 => start with player 0
	}
	
	/**
	 switches to next player
	 */
	void nextPlayer () {
		assert precondition (currentPlayer < numPlayers, 
				"currentPlayer ["+currentPlayer+"] must be < "+numPlayers+
				".\nThough the algorithm is tolerant to that, it means something strange has happened.");
		
		currentPlayer = (currentPlayer+1)%numPlayers;
		ConnectedPlayer player = players.get(currentPlayer);
		Utility.debug ("current player is now "+player.toString());

		if (player.isConnected()) {
			//	signal readiness to set to player
			player.getProtocol().awaitMove ();
		}
		else {
			Utility.bitch(new Throwable ("Sorry - the current player disconnected.\n" +
					"Handling this condition is not yet implemented."));
			System.exit(0);
		}
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
		return setStone (p.getColour(), x, y, z);
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
		assert precondition ((col >= Colour.BLACK && col <= Colour.WHITE), 
				"color must lie between "+Colour.name(Colour.BLACK)+" and "+Colour.name(Colour.WHITE));
		
		if (x < 1 || x > getBoardSize () ||
				y < 1 || y > getBoardSize () ||
				z < 1 || z > getBoardSize ())
			return false;
		
		//  TODO: check for ko's
		if (stones[x][y][z] == Colour.EMPTY) {	//  able to set?	
			stones[x][y][z] = col;				//  fill board position	
			xset = x; yset = y; zset = z;		//  remember last set position
			
			checkArea ();						//  check for captives
			moveBuffer.add (new Move (x, y, z, col));		//  remember this move
			Utility.debug (moveBuffer.size ()+": ("+x+", "+y+", "+z+"), "
					+Liberty (x, y, z, col, false)+" liberties");

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
		assert precondition (p != null, "Player must not be null");
		assert precondition (p.getProtocol() != null, "Protocol must not be null");
		
		p.getProtocol().sendSize(getBoardSize (0), getBoardSize (1), getBoardSize (2));

		p.getProtocol().startBoardTransmission();
		for (int x = 1; x <= getBoardSize (0); x++)
			for (int y = 1; y <= getBoardSize (1); y++)
				for (int z = 1; z <= getBoardSize (2); z++) {
					p.getProtocol().transmitStone (
							getStone (x, y, z), x, y, z);
				}
		p.getProtocol().sendBoard();
	}
	
	/**
	 sends the whole board to all players
	 */
	void updateBoard () {
		ListIterator<ConnectedPlayer> i = players.listIterator();
		while (i.hasNext()) {
			ConnectedPlayer player = i.next();
			updateBoard (player);
		}
	}

	/** 
	 * reimplemented because it is abstract in the base class; makes no sense  
	 * with all players represented by a Player (or ConnectedPlayer) object.
	 * should be removed soon in the base class. 
	 */	
	void sendMessage (int i, String message) {
		assert precondition (i >= 0 && i < numPlayers, 
				"Player must not be between 0 and "+numPlayers);
		Utility.bitch(new Throwable ("sendMessage (int, String) should not be used anymore." +
				" Replace it with sendMessage (ConnectedPlayer, String)."));
		sendMessage (players.get (i), message);
	}

	/**
	 send a text message to one player
	 @param player the addressee
	 @param message the message to be sent
	 */
	void sendMessage (ConnectedPlayer player, String message) {
		assert precondition (player != null, "Player must not be null");
		assert precondition (player.getProtocol() != null, "Protocol must not be null");
		player.getProtocol().message(message);
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
		
		assert precondition (x > 0 && x <= getBoardSize(0) &&
				y > 0 && y <= getBoardSize(1) &&
				z > 0 && z <= getBoardSize(2),
				"point to check must lie inside the board!");

//		Utility.debug ("TODO: Fix Liberties()!\n" +
//				"Save the whole board and set EVERY visited grid place to OCCUPIED.");
		
		
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

		//	TODO: check that the board is unchanged before returning
		
		return liberty;
	}    
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          OVERRIDDEN METHODS END                                        //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////	

	void addPlayer (ConnectedPlayer player) {
		
		//  create a thread to handle communications with the client 
		//  and add it to the thread list
		player.setProtocol(new GameProtocol (player, this));

		//	check whether wanted color is already used
		if (!checkColor (player)) return;		//	TODO better error handling
		
		//  tell the client its color
		setColor (player);
		
		//  send board size and board to client
		updateBoard (player);

		//  start the created thread
		player.getProtocol().start ();
		
		Utility.debug("player "+player.getUsername()+" connected from "+
				player.getClientSocket().getInetAddress().getHostName());

		player.setID(players.size());
		
		players.add (player);

		//	if all players have connected, start the game automatically
		if (players.size() == numPlayers) {
			new GameThread(this).start();
			Utility.debug("GameThread started!");
		}
		
		if (currentPlayer > -1) {
			player.getProtocol().startGame ();
			updateBoard (player);
			if (player.getID() == currentPlayer)
				player.getProtocol().awaitMove();
		}
	
	}
		
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          PROTECTED SECTION 		                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////

	/** check the color of a connecting player is already used
	 *  @param player ConnectedPlayer about to be added to the Game 
	 */
	protected boolean checkColor(ConnectedPlayer player) {
		ListIterator<ConnectedPlayer> i = players.listIterator();
		while (i.hasNext()) {
			if(i.next().getColour() == player.getColour())
				return false;
		}
		return true;
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
	protected void connectWith (Player player) {
		assert precondition (player.getID() > 0 && player.getID() <= numPlayers,
				"Player must lie between 1 and "+numPlayers);
		
		Utility.debug ("waiting for player "+player.getID()
				+(!player.getUsername().equals("")?	" ["+player.getUsername ()+"]":	"")+
				" to connect");
		
		ConnectedPlayer activePlayer;
		
		while (true) {					//	loop until connection succeeds
			try {
				//  (blocking) accept() a client, creating a client socket
				Socket clientSocket = serverSocket.accept();
				
				activePlayer = new ConnectedPlayer (player, clientSocket);
				
				if (!readUsername(activePlayer)) continue;
				
				addPlayer (activePlayer);
				
			} catch (IOException e) {
				Utility.bitch (new Throwable ("Accept failed: "+serverPort));
				e.printStackTrace ();
				System.err.println (e.getMessage());
				continue;	//	try it again, although there's not much hope.
			}
			
			Utility.debug ("Client "+(player.getID()+1)+" connected from "+
					activePlayer.getClientSocket().getInetAddress ().getHostAddress());
			
			assert postcondition (players.contains(activePlayer), 
			"Active player must be in the player list when finished!");
			assert postcondition (players.get(player.getID()).isConnected(),
					"Player "+player.getID()+" must be connected when finished!");
			
			return;
		}
	}

/**	read the player's name from in and set it in the Player object	or compare 
 	it if it's already set in the object */
	boolean readUsername (ConnectedPlayer player) {
		try {
			String username = player.getInStream().readLine ();
			if (!player.hasUsername()) {	//	new player connecting, player not yet set
				//	check whether a player of name username is already connected
				for (int i = 0; i < players.size(); i++) {
					//	if so, reject this name:
					//	EXCEPT if you are the player who has disconnected	[TODO]
					if (players.get(i).getUsername().equals (username)) {
						Utility.debug(username+" tried to connect, but is already connected!");
						player.getOutStream().println(		//	no Protocol exists yet, complain manually
								"go away, you're already connected!");
						player.getClientSocket().close();

						return false;		//	next connection attempt								
					}
				}
				//	if current player is not yet connected, we can continue:
				player.setUsername(username);
				player.setColour(player.getID());	//	the number of the initial Player object denotes its colour
			}
			//	player reconnects after a disconnection
			else if (username.equals(player.toString())) { 
				//	replace entry in player list with active player
				player.setUsername(username);
				for (int i = 0; i < players.size(); i++) {
					if (players.get(i).getUsername().equals (username)) {
						players.set(i, player);
						break;
					}
				}
				Utility.bitch(new Throwable("Didn't find the current Player in the list of " +
						"active Players, even if its name matches! Fscking strange... " +
						"I'm outta here!"));
				System.exit(0);
			}
			//	we wait for a specific player to reconnect, but 
			//	someone else connects
			else {
				Utility.debug(username+" tried to connect, but we want user "+
						player+" to connect!");
				player.getOutStream().println(				//	no Protocol exists yet, complain manually
						"message go away, we want user "+player+" to connect!");
				player.getClientSocket().close();
				
				return false;		//	next connection attempt
			}
			
			//	if we reach  this point, we have a connected, accepted
			//	player with a defined username.
			Utility.debug(player.getUsername()+" connected from "
					+player.getClientSocket().getInetAddress().getHostAddress());
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println (e.getMessage());
			return false;										//	try it again, although there's not much hope.
		}
		player.out.println("ok");

		return true;
	}

	/**
	 update or change the color of a player 
	 @param p the player
	 */
	protected boolean setColor (ConnectedPlayer p) {
		assert precondition (p.isConnected(), "Player must be connected");
		assert precondition (p.getProtocol() != null, "Protocol must exist");
		
		p.getProtocol().setColour(p.getColour());
		return true;
	}
	
	/**
	 set the handicap of a player
	 @param player the player making the request
	 @param handicap wanted handicap; will be normalized taking the handicaps
	 of all other players into account
	 */
	protected void setHandicap (Player player, int h) {
		assert precondition (player.getID() > 0 && player.getID() <= numPlayers,
				"Player must lie between 1 and "+numPlayers);
		assert precondition (h <= MAX_HANDICAPS && h >= 0,
				"Handicaps must lie between 0 and "+MAX_HANDICAPS);
		
		player.setHandicap(h);
		
		//  find out whether smallest handicap of all players is > 0
		int hmin = MAX_HANDICAPS;
		for (int i = 0; i < players.size(); i++) {
			Player p = players.get (i);
			if (p.getHandicap() < hmin)	hmin = p.getHandicap();
		}
		
		//  if so, subtract its value from all handicaps, so that handicaps
		//  start at 0 again
		//	TODO this is not safe for numPlayers > 2, as the handicaps may be
		//	renormalized REPEATEDLY, leading to too small values
		for (int i = 0; i < players.size(); i++) {
			Player p = players.get (i);
			p.setHandicap(p.getHandicap()-hmin);
		}

		assert postcondition (player.getHandicap() <= MAX_HANDICAPS && player.getHandicap() >= 0,
				"Handicaps must lie between 0 and "+MAX_HANDICAPS);
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
		assert precondition (players.size() == 1, "players.size() must be 1!");
				
		for (int i = 1; i < numPlayers; i++) {
			Utility.debug ("waiting for "+(numPlayers-i)
					+" more clients to connect...");
			connectWith (new Player (i));
		}
		
		assert postcondition (players.size() == numPlayers, 
				"players.size() must be "+numPlayers+"!");
	}
		
	
	/**
	 check the whole grid for captives<br>
	 clear any captives found<br>
	 THERE SHOULD BE A MORE DESCRIPTIVE NAME FOR THIS FUNCTION<br>
	 */
	protected void checkArea () {
		assert precondition (xset > 0 && xset <= this.getBoardSize(0) &&
				yset > 0 && yset <= this.getBoardSize(1) &&
				zset > 0 && zset <= this.getBoardSize(2),
				"last set point must lie inside the board!");
		
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
		assert precondition (x >= 0 && x <= this.getBoardSize(0)+1 &&
				y >= 0 && y <= this.getBoardSize(1)+1 &&
				z >= 0 && z <= this.getBoardSize(2)+1,
				"point ["+x+", "+y+", "+z+"] to check must lie inside the board!");
		
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
		assert precondition (x >= 0 && x <= this.getBoardSize(0)+1 &&
				y >= 0 && y <= this.getBoardSize(1)+1 &&
				z >= 0 && z <= this.getBoardSize(2)+1,
				"point ["+x+", "+y+", "+z+"] to check must lie inside the board!");

		if (stones[x][y][z] != current) return;            	//  nothing  to  clear
		
		stones[x][y][z] = Colour.EMPTY;                     //  clear  this  point
		
		Utility.debug ("cleared ("+x+", "+y+", "+z+")");
		
		//  TODO: does this need to be set in the move buffer?
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
			ListIterator<ConnectedPlayer> i = players.listIterator();
			while (i.hasNext())
				i.next().getProtocol().message (msg);
		}
		catch (NullPointerException e) { 
			//	this catch is necessary because the GoGrid c'tor calls 
			//	setBoardSize(), which in the Game subclass in turn calls
			//	broadcast(), which, at construction time, accesses a null 
			//	pointer. 
			//	maybe this catch is a bad solution, but it Works For Me (TM).
		}
	}
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION 		                                      //
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

	protected Socket clientSocket = null;

	/**
	 <tt>ConnectedPlayer</tt>s representing the participant - 
	 */
	protected ArrayList<ConnectedPlayer> players = new ArrayList<ConnectedPlayer> ();
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION ENDS                                        //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
		
}