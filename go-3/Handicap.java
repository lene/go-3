import javax.media.j3d.*;
//import com.sun.j3d.utils.universe.*;
import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;
//import com.sun.j3d.utils.applet.MainFrame;
//import java.awt.*;
//import javax.vecmath.*;

class Handicap extends GridObject {

    private static float radius = 0.10f;
    
    public Handicap (int s, int x, int y, int z) {
	super (s);
	//	System.err.println ("Handicap @ ("+x+", "+y+", "+z+")");
    }

    protected Geometry createGeometry (/* ... */) {

	Sphere sph = new Sphere (radius, Primitive.ENABLE_GEOMETRY_PICKING, 4);
	Shape3D s = sph.getShape ();
	return s.getGeometry ();
	
	/*
	Point3f vertex[] = new Point3f[10];
	vertex[0] = new Point3f (0, 0, 0.10f);
	vertex[1] = new Point3f (0.10f, 0, 0);
	vertex[2] = new Point3f (0.10f, 0.10f, 0);
	vertex[3] = new Point3f (-0.10f, 0.10f, 0);
	vertex[4] = new Point3f (-0.10f, -0.10f, 0);
	vertex[5] = new Point3f (0, 0, -0.10f);
	vertex[6] = vertex[1];
	vertex[7] = vertex[2];
	vertex[8] = vertex[3];
	vertex[9] = vertex[4];
	int stripCounts[] = { 5, 5 };
	
	TriangleFanArray t = new TriangleFanArray (vertex.length,
						   GeometryArray.COORDINATES, stripCounts );

	t.setCoordinates (0, vertex);
	
	return t;
	*/
    }
	
}
