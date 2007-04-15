/**
 * 
 */
package net.hyperspacetravel.go3.client;

import net.hyperspacetravel.go3.GoGrid;

/**
 * @author helge
 *
 */
public interface ChatListener {
	public void message();
	public void setGrid(GoGrid _grid);

}
