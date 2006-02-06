
//

import javax.media.j3d.*;
//import com.sun.j3d.utils.picking.PickTool;
import java.awt.*;
import java.awt.event.*;
import java.util.Enumeration;
import javax.vecmath.*;

//

class SimpleBehavior extends Behavior {
	
	SimpleBehavior (GridDisplay J, TransformGroup tg) {
		this.J = J;
		targetTG = tg;
	}
	
	public void initialize () {
		//	wakeupOn (new WakeupOnAWTEvent (KeyEvent.KEY_PRESSED));
		wakeupOn (new WakeupOnAWTEvent (KeyEvent.KEY_PRESSED));
	}
	
	public void processStimulus (Enumeration criteria) {
		WakeupCriterion wakeup;
		AWTEvent[] event;
		int eventId;
		
		while (criteria.hasMoreElements()) {
			wakeup = (WakeupCriterion) criteria.nextElement();
			if (wakeup instanceof WakeupOnAWTEvent) {
				event = ((WakeupOnAWTEvent)wakeup).getAWTEvent();
				for (int i=0; i<event.length; i++) { 
					eventId = event[i].getID();
					if (event[i] instanceof KeyEvent) {
						double delta = 5*Math.PI/180;
						KeyEvent eventKey = (KeyEvent) event[i];
						if (eventId == KeyEvent.KEY_PRESSED) {
							int keyCode = eventKey.getKeyCode();
							
							int xc = J.xc (), yc = J.yc (), zc = J.zc ();
							
							//  compute current rotation angles
							Transform3D currentTransform = new Transform3D ();
							targetTG.getTransform (currentTransform);
							Matrix3d rot = new Matrix3d ();
							currentTransform.get (rot);
							
							double cosX =  rot.getElement (1, 1),
							cosY =  rot.getElement (0, 0),
							sinX = -rot.getElement (2, 1),
							sinY =  rot.getElement (0, 2);
							if (cosX > 1) cosX = 1;
							if (cosX < -1) cosX = -1;
							if (cosY > 1) cosY = 1;
							if (cosY < -1) cosY = -1;
							angleX = Math.acos (cosX);
							angleY = Math.acos (cosY);
							if (sinX > 0) angleX = -angleX;
							if (sinY < 0) angleY = -angleY;
							/*
							 Utility.debug (" before:  c_x = "+cosX+
							 " s_x = "+sinX);
							 Utility.debug ("          a_x = "+Math.toDegrees (angleX)+"?");
							 Utility.debug ("          c_y = "+cosY+
							 " s_y = "+sinY);
							 Utility.debug ("          a_y = "+Math.toDegrees (angleY)+"?");
							 */
							if (eventKey.isShiftDown ()) {
								delta *= 9;
							}
							if (eventKey.isAltDown () || eventKey.isMetaDown ()) {
								delta /= 5;
							}
							if (eventKey.isAltGraphDown ()) { }
							if (eventKey.isControlDown ()) { }
							switch (keyCode) {
							//  rotating the grid
							case KeyEvent.VK_NUMPAD8:
							case KeyEvent.VK_UP:        angleX -= delta;	break;
							case KeyEvent.VK_NUMPAD2:
							case KeyEvent.VK_DOWN:      angleX += delta;	break;
							case KeyEvent.VK_NUMPAD4:
							case KeyEvent.VK_LEFT:      angleY -= delta;	break;
							case KeyEvent.VK_NUMPAD6:
							case KeyEvent.VK_RIGHT:     angleY += delta;	break;
							case KeyEvent.VK_NUMPAD1:   angleX += delta;
							angleY -= delta;	break;
							case KeyEvent.VK_NUMPAD3:   angleX += delta;
							angleY += delta;    	break;
							case KeyEvent.VK_NUMPAD7:   angleX -= delta;
							angleY -= delta;    	break;
							case KeyEvent.VK_NUMPAD9:   angleX -= delta;
							angleY += delta;    	break;
							case KeyEvent.VK_NUMPAD5:   angleX = 0; angleY = 0;	break;
							//  setting a stone
							case KeyEvent.VK_SPACE:		J.setStone ();
							break;
							//  moving the cursor
							case KeyEvent.VK_X:	    	xc += (eventKey.isShiftDown ()? 1: -1);
							break;
							case KeyEvent.VK_Y:	    	yc += (eventKey.isShiftDown ()? 1: -1);
							break;
							case KeyEvent.VK_Z:	    	zc += (eventKey.isShiftDown ()? 1: -1);
							break;
							//
							case KeyEvent.VK_L:	    	if (eventKey.isControlDown ())
								J.Liberty ();
							break;
							
							case KeyEvent.VK_Q:	    	if (eventKey.isControlDown ())
								J.exit ();
							break;
							
							default:  						break;
							}
							
							if (true) {
								J.setCursor (xc, yc, zc);
								rotateX.rotX (angleX);
								rotateY.rotY (angleY);
								rotateX.mul(rotateY);
								targetTG.setTransform (rotateX);
								/*
								 Utility.debug (" after:  c_x = "+Math.cos (angleX)+
								 "         s_x = "+Math.sin (angleX));
								 Utility.debug ("         a_x = "+Math.toDegrees (angleX)+"?");
								 Utility.debug ("         c_y = "+Math.cos(angleY)+
								 "         s_y = "+Math.sin (angleY));
								 Utility.debug ("         a_y = "+Math.toDegrees (angleY)+"?");
								 */
							}
						}
					}
				}
			}
		}
		wakeupOn (new WakeupOnAWTEvent (KeyEvent.KEY_PRESSED));
	}
	
	private GridDisplay J;
	private TransformGroup targetTG;
	private Transform3D rotateX = new Transform3D (), rotateY = new Transform3D ();
	private double angleX = 0.0, angleY = 0.0;
}

