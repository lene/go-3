package net.hyperspacetravel.go3.client.gui;

import javax.media.j3d.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Enumeration;
import javax.vecmath.*;



class SimpleBehavior extends Behavior {
	
	SimpleBehavior (GridDisplay J, TransformGroup tg) {
		this.J = J;
		targetTG = tg;
	}
	
	public void initialize () {
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

							calculateAngles (rot);

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
							case KeyEvent.VK_UP:        
								angleX -= delta;	break;
							case KeyEvent.VK_NUMPAD2:
							case KeyEvent.VK_DOWN:      
								angleX += delta;	break;
							case KeyEvent.VK_NUMPAD4:
							case KeyEvent.VK_LEFT:      
								angleY -= delta;	break;
							case KeyEvent.VK_NUMPAD6:
							case KeyEvent.VK_RIGHT:     
								angleY += delta;	break;
							case KeyEvent.VK_NUMPAD1:   
								angleX += delta;
								angleY -= delta;	break;
							case KeyEvent.VK_NUMPAD3:   
								angleX += delta;
								angleY += delta;    	break;
							case KeyEvent.VK_NUMPAD7:   
								angleX -= delta;
								angleY -= delta;    	break;
							case KeyEvent.VK_NUMPAD9:   
								angleX -= delta;
								angleY += delta;    	break;
							case KeyEvent.VK_NUMPAD5:   
								angleX = 0; angleY = 0;	break;
							//  setting a stone
							case KeyEvent.VK_SPACE:		
								J.setStone ();
								break;
							//  moving the cursor
							case KeyEvent.VK_X:	    	
								xc += (eventKey.isShiftDown ()? 1: -1);
								break;
							case KeyEvent.VK_Y:	    	
								yc += (eventKey.isShiftDown ()? 1: -1);
								break;
							case KeyEvent.VK_Z:	    	
								zc += (eventKey.isShiftDown ()? 1: -1);
								break;
							//  calculating liberties
							case KeyEvent.VK_L:	    	
								if (eventKey.isControlDown ()) J.Liberty ();
								break;
							//	grid transparency
							case KeyEvent.VK_G:
								if (eventKey.isControlDown ()) J.transparencyUp();
								if (eventKey.isShiftDown ()) J.transparencyDown();
								break;
							//  quit
							case KeyEvent.VK_Q:	    	
								if (eventKey.isControlDown ()) J.exit ();
								break;
							
							default:  		
								break;
							}
							
							J.setCursor (xc, yc, zc);
							rotateX.rotX (angleX);
							rotateY.rotY (angleY);
							rotateX.mul(rotateY);
							J.setTransform (rotateX);
						}
					}
				}
			}
		}
		wakeupOn (new WakeupOnAWTEvent (KeyEvent.KEY_PRESSED));
	}

	private void calculateAngles (Matrix3d rot) {
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
	}
	private GridDisplay J;
	private TransformGroup targetTG;
	private Transform3D rotateX = new Transform3D (), rotateY = new Transform3D ();
	private double angleX = 0.0, angleY = 0.0;
}
