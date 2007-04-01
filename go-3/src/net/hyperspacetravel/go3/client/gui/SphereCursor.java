package net.hyperspacetravel.go3.client.gui;

import javax.media.j3d.Shape3D;

import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;


public class SphereCursor extends Cursor {
	
	////////	CONSTANTS	////////
	private static float RADIUS = 0.5f;
	private static int NUM_SEGMENTS = 16;
	
	////////	C'TORS		////////
	/** default constructor: creates a cursor of default color */
	public SphereCursor () {
		initChildObject(new Sphere(RADIUS, 
								   Primitive.GENERATE_NORMALS|Primitive.ENABLE_APPEARANCE_MODIFY, 
								   NUM_SEGMENTS),
						DEFAULT_COLOUR);
		object.getShape(Sphere.BODY).setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
	}
	
	public SphereCursor (int c) {
		initChildObject(new Sphere(RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS),
				c);
		object.getShape(Sphere.BODY).setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
	}
}
