/**
 * 
 */
package net.hyperspacetravel.go3.client;

/**
 * @author helge
 *
 */
public interface CursorListener {
	void notifyCursor(int x, int y, int z);
	void activate (boolean state);
}
