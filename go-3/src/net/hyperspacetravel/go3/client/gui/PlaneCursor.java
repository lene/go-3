package net.hyperspacetravel.go3.client.gui;

import javax.media.j3d.Appearance;
import javax.media.j3d.Shape3D;

import net.hyperspacetravel.go3.GameBase;

import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;


public class PlaneCursor extends Cursor {
	
	////////	CONSTANTS	////////
	private static float RADIUS = 0.5f;
	private static int NUM_SEGMENTS = 16;
	
	////////	C'TORS		////////
	/** default constructor: creates a cursor of default color */
	public PlaneCursor () {
		initChildObject(new Sphere(RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS),
						DEFAULT_COLOUR);
	}
	
	public PlaneCursor (int c) {
		initChildObject(new Sphere(RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS),
				c);
	}
}
