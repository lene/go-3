
import javax.vecmath.*;

class Exchange {
    static private Point3d intersectionPoint;
    static public void setIntersectionPoint (Point3d in) {
	intersectionPoint = in;
    }
    static public Point3d getIntersectionPoint () {
	return intersectionPoint;
    }
}
