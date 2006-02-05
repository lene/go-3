/*
 * Created on Feb 5, 2006
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

/**
 * @author helge
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
class Player {

	Player (int i) {
			this.i = i;
		}
		
		public int toInt () {
			return i;
		}
		
		public void setName (String username) {	this.username = username;	}
		public String getName () { return this.username; } 
		public String toString() { return getName (); }
		
		protected int i;
		protected String username = "";
}
