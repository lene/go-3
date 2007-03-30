/**
 * 
 */
package net.hyperspacetravel.go3.client;


/**
 * @author helge
 *
 */
public class SimpleCursorListener implements CursorListener {

	/* (non-Javadoc)
	 * @see net.hyperspacetravel.go3.client.gui.CursorListener#notifyCursor(int, int, int)
	 */
	public void notifyCursor(int x, int y, int z) {
		System.out.println("SimpleCursorListener::notifyCursor("+x+", "+y+", "+z+")");
	}

	/* (non-Javadoc)
	 * @see net.hyperspacetravel.go3.client.CursorListener#activate(boolean)
	 */
	public void activate(boolean state) {
		System.out.println("SimpleCursorListener::activate("+(state? "true": "false")+")");
		
	}
	

}
