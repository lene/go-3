
/* Created on Feb 5, 2006
 */

/**
 * @author helge
 */

class Player {
	
	Player (int i) {
		this.number = i;
	}

	Player (Player p) {
		this.number = p.toInt();
		this.username = p.getUsername();
		this.handicap = p.getHandicap();
		this.colour = p.getColour();
	}
	
	public int toInt () { return number; }
	public String toString() { return getUsername (); }
	
	public void setUsername (String username) {	this.username = username;	}
	public String getUsername () { return this.username; } 
	
	public int getHandicap() { return handicap;	}
	public void setHandicap(int handicap) {	this.handicap = handicap; }

	public int getColour() { return colour; }
	public void setColour(int colour) { this.colour = colour; }
	
	protected int number = -1;
	protected String username = "";
	protected int handicap = 0;
	protected int colour = Colour.UNDEFINED;
}
