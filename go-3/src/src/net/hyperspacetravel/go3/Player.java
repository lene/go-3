package net.hyperspacetravel.go3;

/* Created on Feb 5, 2006
 */

/**
 * @author helge
 */

public class Player extends GameBase {
	
	/** basic player c'tor: give an ID
	 *  @param i
	 */
	public Player (int i) { this.id = i; }
	
	/** copy c'tor
	 *  @param p
	 */
	Player (Player p) {
		assert precondition ((p != null), 
				"Player object to copy  must not be null");
		
		this.id = p.getID();
		this.username = p.getUsername();
		this.handicap = p.getHandicap();
		this.colour = p.getColour();
	}
	
	public String toString() { return getUsername (); }
	
	public int getID () { return id; }
	public void setID (int id) { this.id = id; }
	
	public void setUsername (String username) {	
		this.username = username;
		hasname = true;
	}
	public String getUsername () { return this.username; } 
	public boolean hasUsername () { return hasname; }
	
	public int getHandicap() { return handicap;	}
	public void setHandicap(int handicap) {	
		assert precondition ((handicap >= 0 && handicap <= MAX_HANDICAPS), 
				"Handicaps must lie between 0 and "+MAX_HANDICAPS);
		this.handicap = handicap; }
	
	public int getColour() { return colour; }
	public void setColour(int colour) { 
		assert precondition ((colour >= Colour.BLACK && colour <= Colour.WHITE), 
		"color must lie between "+Colour.name(Colour.BLACK)+" and "+Colour.name(Colour.WHITE));

		this.colour = colour; }
	
	public int getWantedBoardSize() { return wantedBoardSize; }

	public void setWantedBoardSize(int wantedBoardSize) {
		assert precondition (wantedBoardSize >= 3 && wantedBoardSize <= GoGrid.MAX_GRID_SIZE,
				"Board size must lie between 3 and "+GoGrid.MAX_GRID_SIZE);
		this.wantedBoardSize = wantedBoardSize;
	}

	public int getWantedNumPlayers() { return wantedNumPlayers;	}

	public void setWantedNumPlayers(int wantedNumPlayers) {
		assert precondition (wantedNumPlayers == 2,
				"Anything but two player games is not yet implemented!");
		this.wantedNumPlayers = wantedNumPlayers;
	}
	
	protected int id = -1;
	protected String username = "";
	protected boolean hasname = false;
	protected int handicap = 0;
	protected int colour = Colour.UNDEFINED;
	protected int wantedBoardSize = 3;
	protected int wantedNumPlayers = 2;

}
