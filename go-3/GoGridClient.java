
import java.net.*;
import java.io.*;
import java.util.Vector;


/**
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
*/

class CommandThread extends Thread {
    BufferedReader in;
    GoGridClient client;

    CommandThread (GoGridClient client, BufferedReader in) {
	super ();
	this.client = client;
	this.in = in;
    }

    public void run () {
	while (true) {
	    Utility.debug ("");
	    String inputLine = new String ();

	    try {
		inputLine = in.readLine ();
	    } catch (IOException e) {
		Utility.bitch (new Throwable ("error reading line from socket!"));
		break;
	    }
	    if (inputLine == null) break;

	    lastMessage = inputLine;
	    
	    Utility.debug ("    input: "+inputLine);
		
	    processInput (inputLine);
	}
	Utility.warning ("null input - server terminated!");
	client.exit ();
    }


    /**
       read a command from the server and act on it
    */
    void processInput (String input) {
	
	Utility.debug (input);

	if (input.startsWith ("start game")) {
	    client.startGame ();
	    return;
	}

	if (input.startsWith ("ready")) {
	    client.activate ();
	    return;
	}

	if (input.startsWith ("ok")) {
	    client.deactivate ();
	    defineStatus (true);
	    return;
	}

	if (input.startsWith ("error")) {
	    defineStatus (false);
	    return;
	}

	if (input.startsWith ("size")) {
	    client.setSize (input);
	    return;
	}

	if (input.startsWith ("stones")) {
	    client.updateBoard (input);
	    return;
	}

	if (input.startsWith ("message")) {
	    System.out.println (Utility.getArgs (input, 2));
	    //  client.message (Utility.getArgs (input, 2));
	    return;
	}
	if (input.startsWith ("liberties")) {
	    try {
		liberties = Integer.parseInt (Utility.getArg (input, 2));
	    } catch (NumberFormatException e) {
		Utility.debug ("NumberFormatException: "+input);
		liberties = -1;
	    } 
	    return;
	}
	/*
	if (input.startsWith ("size")) {
	    Utility.debug ("waiting for board...");
	    int xsize, ysize, zsize;
	    try {
		xsize = Integer.parseInt (Utility.getArg (input, 2));
		ysize = Integer.parseInt (Utility.getArg (input, 3));
		zsize = Integer.parseInt (Utility.getArg (input, 4));
	    } catch (NumberFormatException e) {
		Utility.debug ("NumberFormatException: "+input);
		return;
	    } 
	    
	    client.updateBoard (xsize, ysize, zsize);

	    return;
	}
	*/
	Utility.debug ("input sucks: "+input);
    }

    
    protected int liberties;
    int liberties () { return liberties; }
    void liberties (int l) { liberties = l; }

    protected boolean status = false;
    boolean status () { Utility.debug (""); return status; }

    protected boolean statusDefined = false;
    boolean statusDefined () { /*Utility.debug ("");*/ return statusDefined; }
    void undefineStatus () { Utility.debug (""); statusDefined = false; }    
    private void defineStatus (boolean s) {
	Utility.debug ("");
	statusDefined = true;
	status = s;
    }

    protected String lastMessage = new String ();
    String lastMessage () { return lastMessage; }
}

class GoGridClient extends GoGrid {


    /**
       @param s (preliminary) board size
       @param h server's hostname
       @param parent the J3DApp creating this GoGridClient, or <tt>null</tt>
     */
    public GoGridClient (int s, String h, J3DApp parent) {
	super (s);
	
	this.parent = parent;

	setupConnection (h);

	readColor ();

	setupBoard ();

	commandThread = new CommandThread (this, in);
	commandThread.start ();

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
	String input;
	
	BufferedReader keyboard = new BufferedReader (new InputStreamReader (System.in));
	if (parent == null) {					//  no GUI present, use command line
	    while (true) {
		try {
		    System.out.print ("command: ");
		    input = keyboard.readLine ();
		    out.println (input);
		} catch (IOException e) {
		    Utility.warning ("your keyboard has crashed! strange shit happens...");
		    break;
		}
	    }
	}
	else {
	    //  give the communication thread some time to start up
	    Utility.sleep (20);
	}
    }


    /**
       switches to next player
    */
    void nextPlayer () {
	Utility.bitch (new Throwable ("not yet implemented!"));
    }

    /**
       sets a stone of color <tt>currentPlayer</tt>at the cursor position<br>
       if that position is already occupied, or some other error occurs, does
       nothing<br>
    */
    boolean setStone () {
	return setStone (currentPlayer, xc (), yc (), zc ());
    }
    
    /**
       sets a stone of given color at the given position
	if that position is already occupied, returns false
	clears any stones captured by the move
    	@param col color to set
       @param x x position to set
       @param y y position to set
       @param z z position to set
	@return true
	ISSUE: in principle, this function should wait whether the move was
	successful, and return success
    */
    boolean setStone (int col, int x, int y, int z) {
	Utility.debug (""+col+", "+x+", "+y+", "+z);
	String inputLine = new String ();
	
	out.println ("set at "+x+" "+y+" "+z+" "+col);
	return true;						//  THIS SHOULD REFLECT SUCCESS	
    }


    /**
       read the current board from server
    */
    void updateBoard () {
	Utility.debug ("");
	String input = new String ();

	commandThread.undefineStatus ();
	while (! commandThread.statusDefined ())
	    Utility.sleep (10);
	input = commandThread.lastMessage ();

	Utility.debug (input);
	
	if (! input.startsWith ("size")) {
	    Utility.debug ("size line doesn't start with \"size\": "+input);
	    return;
	}
	    
	int xsize, ysize, zsize;
	try {
	    xsize = Integer.parseInt (Utility.getArg (input, 2));
	    ysize = Integer.parseInt (Utility.getArg (input, 3));
	    zsize = Integer.parseInt (Utility.getArg (input, 4));
	} catch (NumberFormatException e) {
	    Utility.debug ("NumberFormatException: "+input);
	    return;
	}

	updateBoard (xsize, ysize, zsize);

    }


    /**
       send a text message to one or all players
      @param player the addressee or -1 for all
      @param message the message to be sent
    */
    void sendMessage (int to, String msg) {
	out.println ("message "+to+" "+msg);
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
    int Liberty (int x, int y, int z, int current, boolean shortCut) {
	commandThread.liberties = -1;
	out.println ("liberties "+x+" "+y+" "+z);
	while (commandThread.liberties == -1)			//  wait until server has replied
	    Utility.sleep (10);					//  sleep 10 msec
	return commandThread.liberties;
    }

    void setSize (String input) {
	Utility.debug (input);
	if (! input.startsWith ("size")) {
	    Utility.debug ("size line doesn't start with \"size\": "+input);
	    return;
	}
	    
	int size = 0;
	for (int i = 0; i < 3; i++) {
	    try {
		size = Integer.parseInt (Utility.getArg (input, 2+i));
		setBoardSize (size, i);
	    } catch (NumberFormatException e) {
		Utility.debug ("NumberFormatException: "+input);
		return;
	    }
	}
	setupBoard ();
	if (parent != null) parent.setBoardSize (size);
    }
    
    void updateBoard (String input) {
	Utility.debug (input);

	if (! input.startsWith ("stones")) {
	    Utility.bitch (new Throwable ("bad board description line: "+input));
	    return;
	}

	for (int i = 0; i < getBoardSize (0)*getBoardSize (1)*getBoardSize (2); i++) {

	    try {
		int color = Integer.parseInt (Utility.getArg (input, 2+i*4)),
		    xread = Integer.parseInt (Utility.getArg (input, 3+i*4)),
		    yread = Integer.parseInt (Utility.getArg (input, 4+i*4)),
		    zread = Integer.parseInt (Utility.getArg (input, 5+i*4));

		stones[xread][yread][zread] = color;
	    } catch (NumberFormatException e) {
		Utility.debug ("NumberFormatException: "+input);
	    }
	    catch (ArrayIndexOutOfBoundsException e) {
		Utility.debug ("ArrayIndexOutOfBoundsException");
	    }
	}

	if (parent != null) parent.repaint ();
	printGrid ();
	Utility.debug ("done");
    }

    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    //          PROTECTED SECTION STARTS                                      //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////


    /**
       when this function is called, the client can set a stone, until
       <tt>deactivate ()</tt> is called
    */
    protected void activate () {
	parent.activate ();
    }

    /**
       when this function is called, the client can't set a stone any more, 
       until <tt>activate ()</tt> is called again
    */
    protected void deactivate () {
	parent.deactivate ();
    }


    /**
       open a connection to the server<br>
       initialize the input and output channels to the server
       @param serverHost the hostname of the server
    */
    protected void setupConnection (String serverHost) {
	try {
	    clientSocket = new Socket(serverHost, serverPort);
	    out = new PrintWriter (clientSocket.getOutputStream(), true);
	    in = new BufferedReader (new InputStreamReader (clientSocket.getInputStream()));
	} catch (IOException e) {
	    Utility.warning ("GoGridClient.setupConnection (): Connect to "+serverHost+
			     " on port "+serverPort+" failed");
	    exit ();
	}
    }


    /**
       read color from server
    */
    protected void readColor () {
	String inputLine = new String ();
	while (!inputLine.startsWith ("color")) {
	    try {
		inputLine = in.readLine ();
		//		Utility.debug (inputLine);
	    } catch (IOException e) {
		Utility.debug ("reading color failed");
		exit ();
	    }
	    try {
		currentPlayer = Integer.parseInt (Utility.getArg (inputLine, 2));
	    } catch (NumberFormatException e) {
		Utility.debug ("wrong format: "+inputLine);
		continue;
	    }
	}
	Utility.debug ("my color is "+currentPlayer);
    }

    /**
       read the board from the server, after the "size" directive has already
       been received
       @param xsize x size which has been read from the size line
       @param ysize y size which has been read from the size line
       @param zsize z size which has been read from the size line
    */
    protected void updateBoard (int xsize, int ysize, int zsize) {
	
	Utility.debug (""+xsize+"x"+ysize+"x"+zsize+" box");

	if (xsize != getBoardSize () || ysize != getBoardSize () || zsize != getBoardSize ()) {	    
	    setBoardSize (Math.max (xsize, Math.max (ysize, zsize)));
	}

	int xread, yread, zread, color;
	String input = new String ();
	
	commandThread.undefineStatus ();
	while (! commandThread.statusDefined ())
	    Utility.sleep (10);
	input = commandThread.lastMessage ();
	/*
	try {
	    input = in.readLine ();
	} catch (IOException e) {
	    Utility.warning ("GoGridClient.updateBoard (): error reading line from socket!");
	}
	*/
	Utility.debug (input);

	if (! input.startsWith ("stones")) {
	    Utility.bitch (new Throwable ("bad board description line: "+input));
	    return;
	}

	for (int i = 0; i < xsize*ysize*zsize; i++) {

	    try {
		color = Integer.parseInt (Utility.getArg (input, 2+i*4));
		xread = Integer.parseInt (Utility.getArg (input, 3+i*4));
		yread = Integer.parseInt (Utility.getArg (input, 4+i*4));
		zread = Integer.parseInt (Utility.getArg (input, 5+i*4));
	    } catch (NumberFormatException e) {
		Utility.warning ("GoGridClient.updateBoard (): NumberFormatException: "+input);
		continue;
	    }

	    try {
		stones[xread][yread][zread] = color;
	    }
	    catch (ArrayIndexOutOfBoundsException e) {
		Utility.warning ("GoGridClient.updateBoard (): ArrayIndexOutOfBoundsException: "
				 +"("+xread+", "+yread+", "+zread+")");
		continue;
	    }
	}

	if (parent != null) parent.repaint ();
	Utility.debug ("done");
    }


    /**
       exit cleanly
    */
    protected void exit () {
	System.exit (0);
    }


    ////////////////////////////////////////////////////////////////////////////
    //                                                                        //
    //          VARIABLES SECTION STARTS                                      //
    //                                                                        //
    ////////////////////////////////////////////////////////////////////////////


    /**

    */
    protected Socket clientSocket;
    
    /**

    */
    protected PrintWriter out;

    /**

    */
    protected BufferedReader in;

    /**

    */
    protected CommandThread commandThread;

    /**

    */
    protected J3DApp parent = null;


    public static void main (String args[]) {
	int s;
	if (args.length == 0) s = 5;
	else s = Integer.parseInt(args[0]);
	String h;
	if (args.length <= 1) h = new String ("localhost");
	else h = args[1];
	GoGridClient client = new GoGridClient (s, h, null);
    }
}

