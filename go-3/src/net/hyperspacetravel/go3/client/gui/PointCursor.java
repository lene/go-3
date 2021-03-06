package net.hyperspacetravel.go3.client.gui;

import javax.media.j3d.Shape3D;

import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;


public class PointCursor extends Cursor {
	
	////////	CONSTANTS	////////
	private  float RADIUS = 0.5f;
	private  int NUM_SEGMENTS = 16;
	
	////////	C'TORS		////////
	/** default constructor: creates a cursor of default color */
	public PointCursor () {
		initChildObject(new Sphere(RADIUS, 
								   Primitive.GENERATE_NORMALS|Primitive.ENABLE_APPEARANCE_MODIFY, 
								   NUM_SEGMENTS),
						DEFAULT_COLOUR);
		object.getShape(Sphere.BODY).setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
	}
	
	public PointCursor (int c) {
		initChildObject(new Sphere(RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS),
				c);
		object.getShape(Sphere.BODY).setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
	}
}
