package net.hyperspacetravel.go3.client.gui;

import javax.media.j3d.Appearance;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.LineArray;
import javax.media.j3d.Shape3D;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3f;

import com.sun.j3d.utils.geometry.Primitive;


public class PlaneCursor extends Cursor {
	
	class CursorPlane extends Primitive {

		public CursorPlane(int plane, int size) {
			Point3f vertex[] = new Point3f[4*size];
			
			for (int x = 0; x < size; x++) {
				vertex[4*x+0] = new Point3f ((plane == 1? x: 0), 
										   	 (plane == 2? x: 0), 
										   	 (plane == 0? x: 0));
				vertex[4*x+1] = new Point3f ((plane == 1? x: (plane == 2? size-1: 0)),
										   	 (plane == 2? x: (plane == 0? size-1: 0)), 
										   	 (plane == 0? x: (plane == 1? size-1: 0)));
				vertex[4*x+2] = new Point3f ((plane == 2? x: 0),
						 				   	 (plane == 0? x: 0), 
						 				   	 (plane == 1? x: 0));
				vertex[4*x+3] = new Point3f ((plane == 2? x: (plane == 1? size-1: 0)),
						 				   	 (plane == 0? x: (plane == 2? size-1: 0)), 
						 				   	 (plane == 1? x: (plane == 0? size-1: 0)));
			}
			
			line = new LineArray (vertex.length, GeometryArray.COORDINATES);
			line.setCoordinates (0, vertex);
			
			lineShape = new Shape3D(line);

			Transform3D translate = new Transform3D ();
			translate.set (new Vector3f ((plane == 0? 0: 1), 
										 (plane == 1? 0: 1), 
										 (plane == 2? 0: 1)));		//  set translation
			
			TransformGroup cursorPos = new TransformGroup (translate);
			cursorPos.addChild(lineShape);
			this.addChild(cursorPos);
		}
		
		public Shape3D getShape(int arg0) { return lineShape; }

		public Appearance getAppearance(int arg0) { return lineShape.getAppearance(); }

		public void setAppearance(Appearance arg0) { lineShape.setAppearance(arg0); }
		
		private LineArray line;
		private Shape3D lineShape;

	}
		
	////////	C'TORS		////////

	public PlaneCursor (int size, int x, int y, int z, int c) {
		CURSOR_LINEWIDTH = 8.f;
		CURSOR_TRANSPARENCY = 0.2f;
		initChildObject(new CursorPlane((x > 0? 0: (y > 0? 1: 2)), size), c);
		object.getShape(0).setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
	}
}
