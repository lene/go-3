
/** contains some global constants, variables, and accessors needed by (almost)
 *  all classes. sort of a global base class. 
 * @author helge
 */

class GameBase {
	
	static final String VERSION = "0.3.5";
	static final String MAINTAINER = "helge.preuss@gmx.net";

	protected static final int DEFAULT_SERVER_PORT = 6666;
	
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
	 sets the board size
	 @param s board size
	 */
	void setBoardSize (int s) {
		for (int i = 0; i < size.length; i++) setBoardSize (s, i);
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

	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          CONSTANTS SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
		
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
