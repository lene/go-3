/**
 * 
 */
package net.hyperspacetravel.go3.client;

import javax.media.j3d.Transform3D;
import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;


/**
 * @author helge
 *
 */
public class SimpleTransformListener implements TransformListener {

	/* (non-Javadoc)
	 * @see net.hyperspacetravel.go3.client.gui.TransformListener#notifyTransform(javax.media.j3d.Transform3D)
	 */
	public void notifyTransform(Transform3D transform) {
		Matrix3d rot = new Matrix3d();
		Vector3d trans = new Vector3d();
		Matrix3d test = new Matrix3d();
		try {
			transform.get(rot);
			transform.get(trans);
		} catch (NullPointerException e) { }
		System.out.println("                       trans = "+trans);
		System.out.println("                       rot   = "+rotationAngles(rot));
	}
	
	/*

R = (Rx*Ry)*Rz

  	| 1   0   0 |   |  cy 0  sy |
  = | 0  cx -sx | * |   0 1   0 | * Rz
  	| 0  sx  cx |   | -sy 0  cy |
  	
  	|    cy   0    sy |   |  cz -sz  0 |
  =	|  sxsy  cx -sxcy | * |  sz  cz  0 |
  	| -cxsy  sx  cxcy |   |   0   0  1 |
  	
  	|         cycz        -szcy    sy |
  = |  sxsycz+cxsz -sxsysz+cxcz -sxcy |
    | -cxsycz+sxsz  cxsysz+sxcz  cxcy |

 */

	protected Vector3d rotationAngles(Matrix3d rot) {
		double rx, ry, rz, cy; 
		
		ry = Math.asin(rot.getElement(0, 2));
		cy = Math.cos(ry); 
		rx = (cy == 0.? 0.: Math.acos(rot.getElement(2, 2)/cy));
		rz = (cy == 0.? 0.: Math.acos(rot.getElement(0, 0)/cy));
		
		return new Vector3d(Math.toDegrees(rx), Math.toDegrees(ry), Math.toDegrees(rz));
	}
	
	protected double rad2deg() {
		return 0.;
	}
}
