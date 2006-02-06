
import java.util.Vector;

/**
 * class Move represents a move on the go grid, consisting of a position
 * and a colour.
 * <p>
 * Constructor:
 * Move (int x, int y, int z, int col)
 * <p> 
 */

class Move {
	int x;
	int y;
	int z;
	int col;
	public Move (int x, int y, int z, int col) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.col = col;
	}
}


abstract class GoGrid {
	
	/**
	 
	 */
	private static final int DEFAULT_SERVER_PORT = 6666;
	
	
	public GoGrid (int s) {
		setBoardSize (s);
		setNumPlayers (DEFAULT_PLAYERS);
	}
	
	
	
	/**
	 starts the game, i.e. makes setting possible
	 */
	abstract void startGame ();
	
	/**
	 switches to next player
	 */
	abstract void nextPlayer ();
	
	/**
	 sets a stone of color <tt>currentPlayer</tt>at the cursor position<br>
	 if that position is already occupied, or some other error occurs, does
	 nothing<br>
	 */
	boolean setStone () {
		return setStone (currentPlayer, xc (), yc (), zc ());
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
	 */    
	abstract boolean setStone (int col, int x, int y, int z);
	
	/**
	 gets the board in a state that is in sync with all other boards
	 */
	abstract void updateBoard ();
	
	/**
	 send a text message to one or all players
	 @param player the addressee or -1 for all
	 @param message the message to be sent
	 */
	abstract void sendMessage (int player, String message);
	
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
	abstract int Liberty (int x, int y, int z, 
			int current, boolean shortCut);
	
	
	/**
	 set a given number of stones randomly<br>
	 with number = 1, can be used as a very dumb computer opponent
	 @param numMoves number of moves to play
	 @param playerToStart player to start game
	 remaining issues:
	 <ul>
	 <li>allow setting into ko's
	 <li>limit number of random choices to make; if a suitable location is
	 <li>not found after N tries, simply pass
	 <li>more intelligent strategies	 
	 </ul>
	 */
	void fakeGame (int numMoves, int playerToStart) {
		
		for (int i = playerToStart-1; i < numMoves+playerToStart-1; i++) {
			boolean free = true;
			while (free) {
				int x = (int) (Math.random ()*getBoardSize (0))+1;	//  generate random position
				int y = (int) (Math.random ()*getBoardSize (1))+1;	
				int z = (int) (Math.random ()*getBoardSize (2))+1;	
				if (stones[x][y][z] == Colour.EMPTY &&		//  check if empty
						Liberty (x, y, z, i%numPlayers+1, true) > 0) {	//  don't set into suicide
					System.out.print ("Computer: ");
					setStone (i%numPlayers+1,  x,y,z);		//  set the stone
					free = false;				//  terminate while loop
				}
			}
		}
	}
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          PROTECTED SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	
	/**
	 allocate the memory to hold all stones<br>
	 set the places on the board to EMPTY<br>
	 place sentinels at the border of the grid<br>
	 */
	protected void setupBoard () {
		stones = new int[size[0]+2][][];
		
		for (int i = 0; i < size[0]+2; i++) {
			stones [i] = new int[size[1]+2][];
			for (int j = 0; j < size[1]+2; j++) {
				stones [i][j] = new int[size[2]+2]; 
				for (int k = 0; k <= size[2]+1; k++)			//  set places on the inside
					stones[i][j][k] = (i == 0 || i > size[0])? Colour.OCCUPIED:	//  to EMPTY, on the border
						(j == 0 || j > size[1])? Colour.OCCUPIED:   	//  to OCCUPIED, as sentinel
							(k == 0 || k > size[2])? Colour.OCCUPIED:
								Colour.EMPTY;
			}     
		}
	}
	
	
	/**
	 print the grid, all z values in a row, x and y values stacked
	 */
	protected void printGrid () {
		for (int x = 1; x <= getBoardSize (); x++) {
			for (int y = 1; y <= getBoardSize (); y++) {
				for (int z = 1; z <= getBoardSize (); z++) 
					System.out.print(stones[x][y][z]);
				System.out.println();
			}
			System.out.println();
		}
	}   
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	
	/**
	 the port on which the server listens for connections<br>
	 this is not an attribute belonging to a Go Grid, but as both server and
	 client need this attribute, it is implemented here
	 */
	protected static int serverPort = DEFAULT_SERVER_PORT;
	/**
	 @return the server port
	 */
	static int getServerPort () { return serverPort; }
	/**
	 @param sp the future server port
	 */
	static void setServerPort (int sp) { serverPort = sp; }
	
	
	/**
	 stores the moves for replays and saves of the game
	 */
//	protected Vector<Move> moveBuffer = new Vector<Move> ();
	
	
	/**
	 cursor position
	 */
	protected int[] xc, yc, zc;
	/**
	 @return cursor x position
	 */
	int xc () { return xc[currentPlayer]; }
	int xc (int player) { return xc[player]; }
	/**
	 @return cursor y position
	 */
	int yc () { return yc[currentPlayer]; }
	int yc (int player) { return yc[player]; }
	/**
	 @return cursor z position
	 */
	int zc () { return zc[currentPlayer]; }
	int zc (int player) { return zc[player]; }
	/**
	 set the cursor, checking for over- and underflow
	 @param x cursor x position
	 @param y cursor y position
	 @param z cursor z position
	 */
	void setCursor (int x, int y, int z) {
		xc[currentPlayer] = Math.max (1, Math.min (size[0], x));//  check for under- and overflow
		yc[currentPlayer] = Math.max (1, Math.min (size[1], y));
		zc[currentPlayer] = Math.max (1, Math.min (size[2], z));
	}
	/**
	 set cursor position for a certain player without checking for over- and
	 underflow
	 @param player player which sets the cursor
	 @param x cursor x position
	 @param y cursor y position
	 @param z cursor z position 
	 */
	void setCursor (int player, int x, int y, int z) {
		xc[player] = x; yc[player] = y; zc[player] = z;
	}
	
	
	/**
	 board size
	 */
	protected int[] size = new int [3];
	/**
	 @return board size
	 */
	int getBoardSize () { return size[0]; }
	/**
	 @param i axis to read [0..2]
	 @return board size in that direction
	 */
	int getBoardSize (int i) { return size[i]; }
	/**
	 sets the board size and allocates the memory for the board
	 @param s board size
	 */
	void setBoardSize (int s) {
		for (int i = 0; i < size.length; i++) setBoardSize (s, i);
		setupBoard ();
	}
	/**
	 @param s board size in a particular axis       
	 @param i axis to set [0..2]
	 */
	protected void setBoardSize (int s, int i) {
		if (s >= MIN_GRID_SIZE && s <= MAX_GRID_SIZE)
			size[i] = s;
		else throw (new IllegalArgumentException ("Board size must lie between "+
				MIN_GRID_SIZE+" and "+MAX_GRID_SIZE));
	}
	
	
	/**
	 number of players
	 */
	protected int numPlayers = DEFAULT_PLAYERS;
	/**
	 @return number of players
	 */
	int getNumPlayers () { return numPlayers; }
	/**
	 @param p number of players
	 */
	void setNumPlayers (int p) {
		numPlayers = p;
		xc = new int[numPlayers+1];
		yc = new int[numPlayers+1];
		zc = new int[numPlayers+1];
	}
	
	
	/**
	 current player
	 */
	protected int currentPlayer = -1;
	/**
	 @return current player
	 */
	int getCurrentPlayer () { return currentPlayer; }
	/**
	 @param p current player
	 */
	void setCurrentPlayer (int p) { currentPlayer = p; }
	
	
	/**
	 three-dimensional array which holds all the stones
	 */
	protected int [][][] stones = null;
	/**
	 @param x x coordinate
	 @param y y coordinate
	 @param z z coordinate
	 @return color of the stone at requested coordinates, or 0 if empty
	 */
	int getStone (int x, int y, int z) {
		return stones[x][y][z];
	}
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION ENDS                                        //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          CONSTANTS SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
	
	static final String VERSION = "0.3.2";
	static final String MAINTAINER = "helge.preuss@gmx.net";
	
	/**
	 minimum allowed grid size in each dimension
	 */
	static final int MIN_GRID_SIZE = 3;
	/**
	 maximum allowed grid size in each dimension
	 */
	static final int MAX_GRID_SIZE = 25;
	
	/**
	 maximum allowed number of handicaps per player
	 */
	static final int MAX_HANDICAPS = 27;
	
	/**
	 maximum allowed number of players
	 */
	static final int MAX_PLAYERS = 2;
	
	/**
	 default number of players to start with
	 */
	static final int DEFAULT_PLAYERS = 2;
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          CONSTANTS SECTION ENDS                                        //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
}
