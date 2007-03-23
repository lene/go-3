package net.hyperspacetravel.go3;

/** contains some global constants, variables, and accessors needed by (almost)
 *  all classes. sort of a global base class. Some classes can't be derived from
 *  it though, because they must be derived from Java3D classes. 
 *  I want multiple inheritance!
 * @author helge
 */

public class GameBase {
	
	static protected final String VERSION = "0.4.0";
	static protected final String MAINTAINER = "Helge Preuss <helge.preuss@gmx.net>";
//	static public final boolean DEBUG = false; 

	protected static final String DEFAULT_SERVER_HOST = "localhost";
	protected static final int DEFAULT_SERVER_PORT = 6666;
	
	protected static final int SERVER_MAX_PLAYERS = 100;
	protected static final int SERVER_MAX_GAMES = SERVER_MAX_PLAYERS/2;

	/**
	 the port on which the server listens for connections<br>
	 this is not an attribute belonging to a Go Grid, but as both server and
	 client need this attribute, it is implemented here
	 */
	protected static int serverPort = DEFAULT_SERVER_PORT;
	/**
	 @return the server port
	 */
	public static int getServerPort () { return serverPort; }
	/**
	 @param sp the future server port
	 */
	public static void setServerPort (int sp) { 
		assert precondition ((sp >= 1024 && sp < 65535), 
		"Port must lie between 1024 and 65535");
		
		serverPort = sp; }

	/**
	 board size
	 */
	protected int[] size = new int [3];
	/**
	 @return board size
	 */
	public int getBoardSize () { return size[0]; }
	/**
	 @param i axis to read [0..2]
	 @return board size in that direction
	 */
	public int getBoardSize (int i) { 
		assert precondition ((i >= 0 && i < 3), 
		"index must lie between 0 and 3");
		return size[i]; 
	}

	/**
	 sets the board size
	 @param s board size
	 */
	public void setBoardSize (int s) {
		assert precondition ((s >= MIN_GRID_SIZE && s <= MAX_GRID_SIZE), 
				"Board size must lie between "+MIN_GRID_SIZE+" and "+MAX_GRID_SIZE);
		for (int i = 0; i < size.length; i++) setBoardSize (s, i);
	}
	/**
	 @param s board size in a particular axis       
	 @param i axis to set [0..2]
	 */
	protected void setBoardSize (int s, int i) {
		assert precondition ((s >= MIN_GRID_SIZE && s <= MAX_GRID_SIZE), 
				"Board size must lie between "+MIN_GRID_SIZE+" and "+MAX_GRID_SIZE);
		assert precondition ((i >= 0 && i < 3), 
				"index must lie between 0 and 3");

		size[i] = s;
	}
	
	
	////////////////////////////////////////////////////////////////////////////
	//																		  //
	//			DESIGN BY CONTRACT CHECKING METHODS							  //
	//																		  //
	////////////////////////////////////////////////////////////////////////////
	
	public static boolean precondition (boolean cond, String msg) {
		if (!cond) throw new TestException (msg);
		return true;
	}

	protected static boolean postcondition (boolean cond, String msg) {
		if (!cond) throw new TestException (msg);
		return true;
	}

	static boolean invariant () {
		if (true)
			throw new TestException (
					"Override GameBase.invariant () to suit your class!");
		return true;
	}

	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          CONSTANTS SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////
		
	/**
	 minimum allowed grid size in each dimension
	 */
	static public final int MIN_GRID_SIZE = 3;
	/**
	 maximum allowed grid size in each dimension
	 */
	static public final int MAX_GRID_SIZE = 25;
	
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

	protected static class TestException extends RuntimeException {
		/**
		 * 
		 */
		private static final long serialVersionUID = -4759830727869270578L;

		public TestException (String msg) { super (msg); }
	}
}
