
/* Created on Feb 5, 2006
 */

/**
 * @author helge
 */

class Player extends GameBase {
	
	/** basic player c'tor: give an ID
	 *  @param i
	 */
	Player (int i) {
		assert precondition ((i >= 0 && i < MAX_PLAYERS), 
				"Player ID must be between 0 and "+MAX_PLAYERS);
		this.id = i;
	}
	
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
	
	protected int id = -1;
	protected String username = "";
	protected int handicap = 0;
	protected int colour = Colour.UNDEFINED;
	protected boolean hasname = false;
}
