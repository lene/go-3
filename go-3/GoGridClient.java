
import java.net.*;
import java.io.*;
import java.util.Stack;

/** basic implementation of a GoGrid client, which is controlled from and 
 * displays on the console.
 * mainly serves as a parent class to a Java3D based client, but can 
 * theoretically run standalone.
 * 
 * @author helge
 */

class GoGridClient extends GoGrid {
	
	/**
	 @param s (preliminary) board size
	 @param h server's hostname
	 @param p port on the server
	 @param u username
	 @param parent the <tt>GridDisplay</tt> creating this <tt>GoGridClient</tt>, or <tt>null</tt>
	 */
	public GoGridClient (int s, String h, int p, String u, GridDisplay parent) {
		super (s);
		
		this.parent = parent;
		
		GoGrid.setServerPort(p);
		setupConnection (h, u);			//	connect with server
		
		readColor ();					//	blocks! TODO move into ClientProtocol
		
		setupBoard ();					//	initialize board structure
		
		clientProtocol = new ClientProtocol (this, in);
		clientProtocol.start ();			//	start reading from server
		
		startGame ();					
	}
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          OVERRIDDEN METHODS START                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	
	/**	 starts the game, i.e. makes setting possible						  */
	void startGame () {
		if (parent == null) {					//  no GUI present, use command line
			String input;			
			BufferedReader keyboard = new BufferedReader (new InputStreamReader (System.in));
			
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
		System.exit(0);
	}
	
	/**
	 sets a stone of color <tt>currentPlayer</tt>at the cursor position<br>
	 if that position is already occupied, or some other error occurs, does
	 nothing<br>
	 */
	boolean setStone () {
		assert precondition (currentPlayer >= 0 && currentPlayer < numPlayers, 
				"player must be between 0 and "+numPlayers);
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
	 TODO: in principle, this function should wait whether the move was
	 successful, and return success
	 */
	boolean setStone (int col, int x, int y, int z) {
		assert precondition ((col >= 0 && col <= Colour.WHITE), 
				"color must lie between 0 and "+Colour.name(Colour.WHITE));
		assert precondition (x >= 0 && x < MAX_GRID_SIZE &&
				y >= 0 && y < MAX_GRID_SIZE &&
				z >= 0 && z < MAX_GRID_SIZE,
				"point ["+x+", "+y+", "+z+"] must lie inside the allowed grid size!");

		//	TODO decouple client code from protocol syntax
		Utility.debug (""+col+", "+x+", "+y+", "+z);
		
		out.println ("set at "+x+" "+y+" "+z+" "+col);
		return true;						//  TODO check for errors	
	}
	
	void setStoneDirectly (int col, int x, int y, int z) {
		assert precondition (col >= 0 && col <= Colour.WHITE, 
				"Colour must be between 0 and "+Colour.WHITE);
		try {
			stones[x][y][z] = col;
		} catch (ArrayIndexOutOfBoundsException e) {
			Utility.debug ("ArrayIndexOutOfBoundsException");
		}
	}
	
	/**
	 read the current board from server
	 */
	void updateBoard () {
		//	make sure this function is never used.
		assert false;
		//	TODO decouple client code from protocol syntax

		String input = new String ();

		//	loop idle waiting for message to be delivered to the ClientProtocol
		clientProtocol.undefineStatus ();
		while (! clientProtocol.statusDefined ()) Utility.sleep (10);
		input = clientProtocol.lastMessage ();	//	then read the last message
		
		Utility.debug (input);
		
		if (! input.startsWith ("size")) {
			Utility.bitch(new Throwable ("size line doesn't start with \"size\": "+input));
			return;
		}
		
		int xsize, ysize, zsize;
		try {
			xsize = Integer.parseInt (Utility.getArg (input, 2));
			ysize = Integer.parseInt (Utility.getArg (input, 3));
			zsize = Integer.parseInt (Utility.getArg (input, 4));
		} catch (NumberFormatException e) {
			Utility.bitch(new Throwable ("NumberFormatException: "+input));
			return;
		}
		
		updateBoard (xsize, ysize, zsize);
	}
	
	
	/**
	 send a text message to one or all players
	 TODO revamp message syntax generally
	 @param player the addressee or -1 for all
	 @param message the message to be sent
	 */
	void sendMessage (int to, String msg) {
		out.println ("message "+to+" "+msg);
	}
	
	/**
	 output a received message
	 @param msg
	 */
	void message (String msg) {
		System.out.println(msg);
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
		assert precondition ((current >= Colour.BLACK && current <= Colour.WHITE), 
				"color must lie between "+Colour.name(Colour.BLACK)+" and "+Colour.name(Colour.WHITE));
		assert precondition (x > 0 && x <= getBoardSize(0) &&
				y > 0 && y <= getBoardSize(1) &&
				z > 0 && z <= getBoardSize(2),
				"point to check must lie inside the board!");
		
		//	TODO decouple client code from protocol syntax
		out.println ("liberties "+x+" "+y+" "+z);	//	request liberties from server
		while (clientProtocol.liberties == -1)		//  wait until server has replied
			Utility.sleep (10);						//  sleep 10 msec
		return clientProtocol.liberties();
	}
	
	void setSize (String input) {
		//	TODO decouple client code from protocol syntax
		assert precondition (input.startsWith ("size"),
				"size line doesn't start with \"size\": "+input);
		
		Utility.debug (input);
		
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
		//	TODO decouple client code from protocol syntax
		assert precondition (input.startsWith ("stones"),
				"bad board description line: "+input);
				
		Utility.debug (input);
		
		for (int i = 0; i < getBoardSize (0)*getBoardSize (1)*getBoardSize (2); i++) {
			
			try {
				int color = Integer.parseInt (Utility.getArg (input, 2+i*4)),
				xread = Integer.parseInt (Utility.getArg (input, 3+i*4)),
				yread = Integer.parseInt (Utility.getArg (input, 4+i*4)),
				zread = Integer.parseInt (Utility.getArg (input, 5+i*4));
				
				stones[xread][yread][zread] = color;
			} catch (NumberFormatException e) {
				Utility.debug ("NumberFormatException: "+e.getMessage()+input);
			}
			catch (ArrayIndexOutOfBoundsException e) {
				Utility.debug ("ArrayIndexOutOfBoundsException");
			}
		}
		
		repaint();

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
	protected void setupConnection (String serverHost, String username) {
		while (true) {							//	loop until successful
			try {
				clientSocket = new Socket(serverHost, serverPort);
				out = new PrintWriter (clientSocket.getOutputStream(), true);
				in = new BufferedReader (new InputStreamReader (clientSocket.getInputStream()));
			} catch (IOException e) {
				Utility.warning ("GoGridClient.setupConnection (): Connect to "+serverHost+
						" on port "+serverPort+" failed");
				System.exit(0);					//	TODO decent error handling
			}
			try {
				out.println(username);
				String ack = in.readLine();
				System.out.println (ack);
				if (!ack.equals("ok")) {
					Utility.debug(ack);
					System.exit (0);			//	TODO decent error handling
					continue;
				}
			} catch (IOException e) { 			//	TODO decent error handling
			}
			return;
		}
	}
	
	
	/**
	 read color from server
	 */
	protected void readColor () {
		//	TODO decouple client code from protocol syntax
		String inputLine = new String ();
		while (!inputLine.startsWith ("color")) {
			try {
				inputLine = in.readLine ();
			} catch (IOException e) {
				Utility.debug ("reading color failed");
				clientProtocol.lostConnection ();		//	TODO decent error handling
			}
			try {
				currentPlayer = Integer.parseInt (Utility.getArg (inputLine, 2));
			} catch (NumberFormatException e) {
				Utility.debug ("wrong format: "+inputLine);
				continue;						//	TODO decent error handling
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
		//	TODO decouple client code from protocol syntax
		assert false;
		
		Utility.debug (""+xsize+"x"+ysize+"x"+zsize+" box");
		
		if (xsize != getBoardSize () || ysize != getBoardSize () || zsize != getBoardSize ()) {	    
			setBoardSize (Math.max (xsize, Math.max (ysize, zsize)));
		}
		
		int xread, yread, zread, color;
		String input = new String ();
		
		clientProtocol.undefineStatus ();
		while (! clientProtocol.statusDefined ())
			Utility.sleep (10);
		input = clientProtocol.lastMessage ();

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
				Utility.warning ("GoGridClient.updateBoard (): NumberFormatException: "+e.getMessage()+input);
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
		
	void repaint () {
		if (parent != null) parent.repaint ();
		if (Utility.getDebugMode ()) printGrid ();
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
	protected ClientProtocol clientProtocol;
	
	/**
	 
	 */
	protected GridDisplay parent = null;
	
	
	public static void main (String args[]) {
		int s;
		if (args.length == 0) s = 5;
		else s = Integer.parseInt(args[0]);
		String h;
		if (args.length <= 1) h = new String ("localhost");
		else h = args[1];
		String u;
		if (args.length <= 2) u = System.getProperty ("user.name")+"@localhost";
		else u = args[2];
		int p;
		if (args.length <= 3) p = GoGrid.getServerPort();
		else p = Integer.parseInt(args[3]);
		
		GoGridClient client = new GoGridClient (s, h, p, u, null);
	}
}

