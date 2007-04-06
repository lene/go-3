package net.hyperspacetravel.go3.client.gui;

import javax.media.j3d.Appearance;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.LineArray;
import javax.media.j3d.Shape3D;
import javax.vecmath.Point3f;

import com.sun.j3d.utils.geometry.Primitive;


public class LineCursor extends Cursor {
	
	class CursorLine extends Primitive {

		public CursorLine(int axis, int length) {
			System.out.println("CursorLine("+axis+", "+length+")");
			Point3f vertex[] = new Point3f[2];
			vertex[0] = new Point3f (0, 0, 0);
			vertex[1] = new Point3f ((axis == 0? length: -1),
									 (axis == 1? length: -1), 
									 (axis == 2? length: -1));
			
			line = new LineArray (vertex.length, GeometryArray.COORDINATES);
			line.setCoordinates (0, vertex);
			
			lineShape = new Shape3D(line);
			
			this.addChild(lineShape);
		}
		
		public Shape3D getShape(int arg0) {
			return lineShape;
		}

		public Appearance getAppearance(int arg0) {
			return lineShape.getAppearance();
		}

		public void setAppearance(Appearance arg0) {
			lineShape.setAppearance(arg0);			
		}
		
		private LineArray line;
		private Shape3D lineShape;

	}
		
	////////	C'TORS		////////

	public LineCursor (int size, int x, int y, int z, int c) {
		initChildObject(new CursorLine((x <= 0? 0: (y <= 0? 1: 2)), size), c);
		object.getShape(0).setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
	}
}
