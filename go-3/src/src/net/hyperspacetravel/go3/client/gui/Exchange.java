package net.hyperspacetravel.go3.client.gui;

import javax.vecmath.*;

/** a helper class to globally, statically store a <tt>Point3d</tt> - to make it  
 *  accessible from <tt>GridDisplay</tt> and <tt>IntersectInfoBehavior</tt>
 * 
 * @author helge
 */

class Exchange {
	static public void setIntersectionPoint (Point3d in) { intersectionPoint = in; }
	static public Point3d getIntersectionPoint () { return intersectionPoint; }
	static private Point3d intersectionPoint;
}
