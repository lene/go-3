
import java.net.*;
import java.io.*;
import java.util.Vector;

class GoGridServer extends GoGrid {

    public GoGridServer (int size) {
	super (size);

	setupBoard ();                                          //  initialize board structure

	reserveVectors ();              	                //  get storage space for client data

	setupConnections ();

	if (true) startGame ();
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
	for (int player = 0; player < numPlayers; player++) {	    	    
	    updateBoard (player);
	    ((GoGridProtocol)proto.elementAt (player)).startGame ();
	}
	nextPlayer ();                                          //  currentPlayer = -1 => start with player 0
    }

    
    /**
       switches to next player
     */
    void nextPlayer () {
	currentPlayer = (currentPlayer+1)%numPlayers;
	Utility.debug ("current player is now "+currentPlayer);
	((GoGridProtocol)proto.elementAt (currentPlayer)).awaitMove ();
	((PrintWriter)out.elementAt (currentPlayer)).println ("ready");
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
	if (stones[x][y][z] == Colour.EMPTY) {			//  able to set?	
	    stones[x][y][z] = col;				//  fill board position	
	    xset = x; yset = y; zset = z;			//  remember last set position

	    checkArea ();					//  check for captives
	    moveBuffer.add (new Move (x, y, z, col));		//  remember this move
	    Utility.debug (moveBuffer.size ()+": ("+x+", "+y+", "+z+"), "
			   +Liberty (x, y, z, col, false)+" liberties");
	    //	    printGrid ();
	    return true;					//  success
	}
	else {
	    Utility.debug ("Position occupied: ("+x+", "+y+", "+z+")");
	    return false;					// failure
	}
    }	    


   /**
      sends the whole board to all players
    */
    void updateBoard () {
	for (int i = 0; i < numPlayers; i++)
	    updateBoard (i);
    }

   /**
      send a text message to one or all players
      @param player the addressee or -1 for all (actually any int < 0)
      @param message the message to be sent
   */
    void sendMessage (int player, String message) {
	if (player < 0) 
	    broadcast ("message "+message);
	else if (player < numPlayers)
		((PrintWriter)out.elementAt (player)).println ("message "+message);
	else 
	    Utility.bitch (new Throwable (""+player+", \""+message+"\"): numPlayers = "+numPlayers));
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
       set up 1 (one) client connection:
       <ul>
           <li>client socket
	   <li>output stream to client
	   <li>input stream from client
	   <li>tell the client its color
	   <li>send board size and board to client
	   <li>start a thread to handle communication with the client
       </ul>
    */
    void connectWith (int player) {
	Utility.debug ("waiting for player "+player+" to connect");
	
	try {
	    //  create a client socket with accept() and insert it into the
	    //  socket list
	    clientSocket.insertElementAt (
					  serverSocket.accept(), 
					  player);

	    //  create an output channel to the client and insert it into 
	    //  the output list
	    out.insertElementAt (
				 new PrintWriter (
						  ((Socket)clientSocket.elementAt (player)).getOutputStream(), true), 
				 player);

	    //  create an input channel from the client and insert it into
	    //  the input list
	    in.insertElementAt (
				new BufferedReader (
						    new InputStreamReader(
									  ((Socket)clientSocket.elementAt (player)).getInputStream())), 
				player);

	    //  tell the client its color
	    setColor (player, player);

	    //  send board size and board to client
	    updateBoard (player);

	    handicap.insertElementAt (new Integer (0), player);

	    //  create a thread to handle communications with the client 
	    //  and add it to the thread list
	    proto.insertElementAt (
				   new GoGridProtocol (player, this, 
						       (BufferedReader)in.elementAt (player), 
						       (PrintWriter)out.elementAt (player)), 
				   player);
		
	    //  start the created thread
	    ((GoGridProtocol)proto.elementAt (player)).start ();

	} catch (IOException e) {
	    Utility.bitch (new Throwable ("Accept failed: "+serverPort));
	    System.exit(0);
	}

	if (currentPlayer >= 0) {
	    ((GoGridProtocol)proto.elementAt (player)).startGame ();
	    updateBoard (player);
	    if (player == currentPlayer)
		((PrintWriter)out.elementAt (currentPlayer)).println ("ready");
	}
	    
	Utility.debug ("Client "+(player+1)+" connected from "+
		       ((Socket)clientSocket.elementAt (player)).getInetAddress ().toString ());

    }

    /**
       sends the whole board to a specified player
       @param player the player who made the request
    */
    void updateBoard (int p) {
	Utility.debug ("Player "+p);

	((PrintWriter)out.elementAt (p)).println ("size "+getBoardSize (0)
						  +" "+getBoardSize (1)
						  +" "+getBoardSize (2));
	String content = "stones ";
	for (int x = 1; x <= getBoardSize (0); x++)
	    for (int y = 1; y <= getBoardSize (1); y++)
		for (int z = 1; z <= getBoardSize (2); z++)
		    content += getStone (x, y, z)+" "+x+" "+y+" "+z+" ";

	((PrintWriter)out.elementAt (p)).println (content);
    }


    /**
       tries to change the color of a player 
       @param oldc current color of the player
       @param newc wanted color
    */
    boolean setColor (int oldc, int newc) {
	if (oldc == newc) {
	    ((PrintWriter)out.elementAt (oldc)).println ("color "+(oldc+1));
	    return true;
	} 
	else {
	    //  move the elements in the corresponding vectors around
	    //  tell the members of the proto vectors about their color
	    Utility.bitch (new Throwable (""+oldc+" != "+newc+"): not yet implemented!"));
	    return false;
	}
    }


    /**
       set the handicap of a player
       @param player the player making the request
       @param handicap wanted handicap; will be normalized taking the handicaps
                       of all other players into account
    */
    void setHandicap (int player, int h) {
	if (h <= MAX_HANDICAPS && h >= 0) 
	    handicap.setElementAt (new Integer (h), player);

	//  find out whether smallest handicap of all players is > 0
	Integer hmin = new Integer (MAX_HANDICAPS+1);
	for (int i = 0; i < numPlayers; i++)
	    if (((Integer)handicap.elementAt (i)).compareTo (hmin) < 0) 
		hmin = (Integer)handicap.elementAt (i);

	//  if so, subtract its value from all handicaps, so that handicaps
	//  start at 0 again
	for (int i = 0; i < numPlayers; i++)
	    handicap.setElementAt (new Integer (
						((Integer)handicap.elementAt (i)).intValue ()
						-hmin.intValue ()),
				   i);
    }


    /**
       @param player the player whose handicap is read
       @return the handicap of the player
    */
    int getHandicap (int player) {
	return ((Integer)handicap.elementAt (player)).intValue ();
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
    protected void reserveVectors () {
	handicap.ensureCapacity (numPlayers);
	clientSocket.ensureCapacity (numPlayers);
	out.ensureCapacity (numPlayers);
	in.ensureCapacity (numPlayers);
	proto.ensureCapacity (numPlayers);
    }	
	

    /**
       set up the server socket<br>
       accept <tt>numPlayers</tt> client connections<br>
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
    protected void setupConnections () {
	try {						
	    serverSocket = new ServerSocket(serverPort);	//  set up the server socket
	} catch (IOException e) {
	    Utility.bitch (new Throwable ("Could not listen on port: "+serverPort));
	    System.exit(0);                                    //  die of ressource starvation
	}

	Utility.debug ("waiting for "+numPlayers+" clients to connect...");
	
	for (int player = 0; player < numPlayers; player++) {
	    connectWith (player);
	}

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
	    if (Liberty (x, y, z, P, true) == 0)                //  check liberties
		clearArea (x, y, z, P);                         //  remove, if zero
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
	
	stones[x][y][z] = Colour.EMPTY;                       //  clear  this  point

	//  ISSUE: does this need to be set in the move buffer?
	//  MoveBuffer[++MoveNbr] = Entry (xset, yset, zset, stones[xset][yset][zset]); 
	
	for (int xx = x-1; xx <= x+1; xx += 2)          	//  check  neighbors :
	    if (getStone (xx, y, z) == current &&               	//  same  color ?
		xx >= 1 && xx <= getBoardSize (0))                    	//  on  grid ?
		clearArea (xx, y, z, current);                  //  then  clear !

	for (int yy = y-1; yy <= y+1; yy += 2)          	//  again...
	    if (getStone (x, yy, z) == current &&               	//  same  color ?
		yy >= 1 && yy <= getBoardSize (1))                    	//  on  grid ?
		clearArea (x, yy, z, current); 

	for (int zz = z-1; zz <= z+1; zz += 2)          	//  and again
	    if (getStone (x, y, zz) == current &&               	//  same  color ?
		zz >= 1 && zz <= getBoardSize (2))                    	//  on  grid ?
		clearArea (x, y, zz, current);
    }


    /**
       send a string message to all connected clients
       @param msg the message to be sent
    */
    protected void broadcast (String msg) {
	try {
	    for (int i = 0; i < numPlayers; i++)
		((PrintWriter)out.elementAt (i)).println (msg);
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
       the connections to the clients, of type <tt>Socket</tt>
    */
    protected Vector clientSocket = new Vector ();

    /**
       <tt>PrintWriter</tt>s as output connections to all clients
    */
    protected Vector out = new Vector ();

    /**
       <tt>BufferedReader</tt>s as input connections from all clients
    */
    protected Vector in = new Vector ();

    /**
       <tt>GoGridProtocol</tt>s handling the input as a thread for each client
    */
    protected Vector proto = new Vector ();

    /**
       the handicaps for each client, stored as <tt>Integer</tt>s
    */
    protected Vector handicap = new Vector ();

    
    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    //          VARIABLES SECTION ENDS                                        //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////

    
    /**
       start a gogrid server
       @param args the command line
       command line: java GoGrid [gridsize]
       MISSING: A BETTER PARSE FUNCTION, SETTING E.G. PORT, NUMBER OF PLAYERS,
       WHATEVER, AND TAKING COMMAND LINE SWITCHES
    */
    public static void main (String args[]) {
	int s;
	if (args.length == 0) s = 3;
	else s = Integer.parseInt(args[0]);
	GoGridServer server = new GoGridServer (s);
    }
}

