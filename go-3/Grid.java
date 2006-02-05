
//

import javax.media.j3d.*;
//import com.sun.j3d.utils.universe.*;
//import com.sun.j3d.utils.geometry.Primitive;
//import com.sun.j3d.utils.geometry.Sphere;
//import com.sun.j3d.utils.applet.MainFrame;
//import java.awt.*;
import javax.vecmath.*;
//import java.applet.Applet;

public class Grid extends GridObject {

    public Grid (int s) {
	super (s);
    }

    //  the index bounds must be changed to run from 1..size instead of 0..size-1!
    protected Geometry createGeometry (/* ... */) {
	Point3f vertex[] = new Point3f[6*size*size];
	for (int z = 0; z <= size; z++)                    //  draw  all  xy-planes
	    for (int xy = 0; xy < size; xy++) {
		vertex[4*(z*size+xy)]   = new Point3f (xy, 0, z);
		vertex[4*(z*size+xy)+1] = new Point3f (xy, (size-1), z);
		vertex[4*(z*size+xy)+2] = new Point3f (0, xy, z);
		vertex[4*(z*size+xy)+3] = new Point3f ((size-1), xy, z);
	    }

	for (int x = 0; x < size; x++)                    //  draw  all  z-lines
	    for (int y = 0; y < size; y++) {
		vertex[4*size*size+2*(x*size+y)]   =  new Point3f (x, y, 0);
		vertex[4*size*size+2*(x*size+y)+1] =  new Point3f (x, y, (size-1));
	    }

	LineArray l = new LineArray (vertex.length, GeometryArray.COORDINATES);
	l.setCoordinates (0, vertex);
	
	return l;
    }
	
}

