package net.hyperspacetravel.go3;

import java.util.ArrayList;

/**
 * class Move represents a move on the go grid, consisting of a position
 * and a colour.
 * <p>
 * Constructor:
 * Move (int x, int y, int z, int col)
 * <p> 
 */

class Move extends GameBase {
	public Move (int x, int y, int z, int col) {

		assert precondition (x >= 0 && x < MAX_GRID_SIZE &&
				y >= 0 && y < MAX_GRID_SIZE &&
				z >= 0 && z < MAX_GRID_SIZE,
				"point ["+x+", "+y+", "+z+"] must lie inside the allowed grid size!");
		assert precondition ((col >= Colour.BLACK && col <= Colour.WHITE), 
				"color must lie between "+Colour.name(Colour.BLACK)+" and "+Colour.name(Colour.WHITE));

		this.x = x;
		this.y = y;
		this.z = z;
		this.col = col;
	}

	private int x, y, z;
	private int col;
}


public abstract class GoGrid extends GameBase {
	
	/**
	 
	 */
	
	public GoGrid (int s) {
		assert precondition ((s >= MIN_GRID_SIZE && s <= MAX_GRID_SIZE), 
				"Board size must lie between "+MIN_GRID_SIZE+" and "+MAX_GRID_SIZE);		
		
		setBoardSize (s);
		setNumPlayers (DEFAULT_PLAYERS);
	}
	
	/**
	 starts the game, i.e. makes setting possible
	 */
	abstract public void startGame ();
	
	/**
	 switches to next player
	 */
	abstract public void nextPlayer ();
	
	/**
	 sets a stone of color <tt>currentPlayer</tt>at the cursor position<br>
	 if that position is already occupied, or some other error occurs, does
	 nothing<br>
	 */
	public boolean setStone () {
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
	abstract public boolean setStone (int col, int x, int y, int z);

	/**
	 gets the board in a state that is in sync with all other boards
	 */
	abstract public void updateBoard ();
	
	/**
	 send a text message to one or all players
	 TODO an int as parameter sucks! use a Player or ConnectedPlayer!
	 @param player the addressee or -1 for all
	 @param message the message to be sent
	 */
	abstract public void sendMessage (int player, String message);
	
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
	abstract public int Liberty (int x, int y, int z, 
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
	public void fakeGame (int numMoves, int playerToStart) {
		assert precondition ((playerToStart >= 0 && playerToStart <= numPlayers), 
				"starting player must be between 0 and "+numPlayers);

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
	 stores the moves for replays and saves of the game
	 */
	protected ArrayList<Move> moveBuffer = new ArrayList<Move> ();
	
	
	/**
	 cursor position
	 */
	protected int[] xc, yc, zc;
	/**
	 @return cursor x position
	 */
	public int xc () { 
		if (currentPlayer >= 0 && currentPlayer <= numPlayers)
			return xc(currentPlayer); 
		else 
			return (getBoardSize(0)+1)/2;
	}
	public int xc (int player) { 
		assert precondition ((player >= 0 && player <= numPlayers), 
				"player must be between 0 and "+numPlayers);
		
		return xc[player]; }
	/**
	 @return cursor y position
	 */
	public int yc () {
		if (currentPlayer >= 0 && currentPlayer <= numPlayers)
		  return yc(currentPlayer); 
		else 
			return (getBoardSize(1)+1)/2;		
	}
	public int yc (int player) { 
		assert precondition ((player >= 0 && player <= numPlayers), 
				"player must be between 0 and "+numPlayers);
		
		return yc[player]; }
	/**
	 @return cursor z position
	 */
	public int zc () { 
		if (currentPlayer >= 0 && currentPlayer <= numPlayers)
			return zc(currentPlayer); 
		else 
			return (getBoardSize(2)+1)/2;
	}
	public int zc (int player) { 
		assert precondition ((player >= 0 && player <= numPlayers), 
				"player must be between 0 and "+numPlayers);

		return zc[player]; }
	/**
	 set the cursor, checking for over- and underflow
	 @param x cursor x position
	 @param y cursor y position
	 @param z cursor z position
	 */
	public void setCursor (int x, int y, int z) {
		assert precondition ((currentPlayer >= 0 && currentPlayer <= numPlayers), 
				"current player ["+currentPlayer+"] must be between 0 and "+numPlayers);
		try {
			xc[currentPlayer] = Math.max (1, Math.min (getBoardSize(0), x));//  check for under- and overflow
			yc[currentPlayer] = Math.max (1, Math.min (getBoardSize(1), y));
			zc[currentPlayer] = Math.max (1, Math.min (getBoardSize(2), z));
		} catch (ArrayIndexOutOfBoundsException e) {
			Utility.debug("game not yet started!");
		}
	}
	/**
	 set cursor position for a certain player without checking for over- and
	 underflow
	 @param player player which sets the cursor
	 @param x cursor x position
	 @param y cursor y position
	 @param z cursor z position 
	 */
	public void setCursor (int player, int x, int y, int z) {
		assert precondition ((player >= 0 && player <= numPlayers), 
				"player must be between 0 and "+numPlayers);

		xc[player] = x; yc[player] = y; zc[player] = z;
	}
	
	
	/**
	 sets the board size and allocates the memory for the board
	 @param s board size
	 */
	public void setBoardSize (int s) {
		super.setBoardSize(s);
		setupBoard ();
	}
	
	
	/**
	 number of players
	 */
	protected int numPlayers = DEFAULT_PLAYERS;
	/**
	 @return number of players
	 */
	public int getNumPlayers () { return numPlayers; }
	/**
	 @param p number of players
	 */
	public void setNumPlayers (int p) {
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
	public int getCurrentPlayer () { return currentPlayer; }
	/**
	 @param p current player
	 */
	public void setCurrentPlayer (int p) { 
		assert precondition ((p >= 0 && p <= numPlayers), 
				"player must be between 0 and "+numPlayers);

		currentPlayer = p; }
	
	
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
	public int getStone (int x, int y, int z) {
		return stones[x][y][z];
	}
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION ENDS                                        //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
	
}