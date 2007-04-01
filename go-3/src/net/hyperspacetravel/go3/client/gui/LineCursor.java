package net.hyperspacetravel.go3.client.gui;

import javax.media.j3d.Appearance;
import javax.media.j3d.Shape3D;

import net.hyperspacetravel.go3.GameBase;

import com.sun.j3d.utils.geometry.Primitive;
import com.sun.j3d.utils.geometry.Sphere;


public class LineCursor extends Cursor {
	
	class CursorLine extends Primitive {
		public Shape3D getShape(int arg0) {
			return object.getShape(arg0);
		}

		public Appearance getAppearance(int arg0) {
			return object.getAppearance(arg0);
		}

		public void setAppearance(Appearance arg0) {
			object.setAppearance(arg0);			
		}

	}
	
	////////	CONSTANTS	////////
	private static float RADIUS = 0.5f;
	private static int NUM_SEGMENTS = 16;
	
	////////	C'TORS		////////
	/** default constructor: creates a cursor of default color */
	public LineCursor () {
		initChildObject(new Sphere(RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS),
						DEFAULT_COLOUR);
	}
	
	public LineCursor (int c) {
		initChildObject(new Sphere(RADIUS, Primitive.GENERATE_NORMALS, NUM_SEGMENTS),
				c);
	}
}
