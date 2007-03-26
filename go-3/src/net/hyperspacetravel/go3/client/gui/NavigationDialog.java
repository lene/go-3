/**
 * 
 */
package net.hyperspacetravel.go3.client.gui;

import java.awt.Frame;
import java.awt.SystemColor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.util.ArrayList;

import javax.media.j3d.Transform3D;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;

import net.hyperspacetravel.go3.Utility;
import net.hyperspacetravel.go3.client.TransformListener;
import net.hyperspacetravel.go3.client.gui.CursorDialog.IndexedChangeListener;
import net.hyperspacetravel.go3.client.gui.CursorDialog.IndexedSliderChangeListener;
import net.hyperspacetravel.go3.client.gui.CursorDialog.IndexedSpinnerChangeListener;
import net.hyperspacetravel.go3.client.gui.CursorDialog.IndexedSpinnerKeyAdapter;

/**
 * @author helge
 *
 */
public class NavigationDialog extends JDialog implements TransformListener {

	class IndexedSliderChangeListener implements ChangeListener {
		
		IndexedSliderChangeListener(int i) { this.index = i; }
		
		public void stateChanged(ChangeEvent arg0) {
			grid.setCursor(navigationSlider.get(0).getValue(), 
						   navigationSlider.get(1).getValue(), 
						   navigationSlider.get(2).getValue());
			navigationTextField.get(index).setText(String.valueOf(navigationSlider.get(index).getValue()));
		}
		
		protected int index = 0;
	}
	
	class IndexedTFActionListener implements ActionListener {

		IndexedTFActionListener(int i) {
			this.index = i;
		}
		
		public void actionPerformed(ActionEvent arg0) {
	        navigationSlider.get(index).setValue(
	        		Double.valueOf(navigationTextField.get(index).getText()).intValue());
		}						
		protected int index = 0;
	}

	class IndexedSpinnerKeyAdapter extends KeyAdapter {

		IndexedSpinnerKeyAdapter(int i) {
			this.index = i;
		}
		
		public void keyTyped(java.awt.event.KeyEvent e) {
	        navigationSlider.get(index).setValue(
	        		Double.valueOf(navigationTextField.get(index).getText()).intValue());
		}
		
		int index = 0;
	}

	/* (non-Javadoc)
	 * @see net.hyperspacetravel.go3.client.TransformListener#notifyTransform(javax.media.j3d.Transform3D)
	 */
	public void notifyTransform(Transform3D transform) {
		Matrix3d rot = new Matrix3d();
		Vector3d trans = new Vector3d();
		transform.get(rot);
		transform.get(trans);
		Vector3d rotAngles = this.rotationAngles(rot);
		navigationTextField.get(0).setText(Double.valueOf(rotAngles.x).toString());
		navigationSlider.get(0).setValue(Double.valueOf(rotAngles.x).intValue());
		navigationTextField.get(1).setText(Double.valueOf(rotAngles.y).toString());
		navigationSlider.get(1).setValue(Double.valueOf(rotAngles.y).intValue());
		navigationTextField.get(2).setText(Double.valueOf(rotAngles.z).toString());
		navigationSlider.get(2).setValue(Double.valueOf(rotAngles.z).intValue());
	}
	
	public NavigationDialog(GridDisplay _grid, Frame frame) {
		super(frame);
		this.grid = _grid;
		this.navigationPanel = new ArrayList<JPanel>(3);
		this.navigationLabel = new ArrayList<JLabel>(3);
		this.navigationTextField = new ArrayList<JTextField>(3);
		this.navigationSlider = new ArrayList<JSlider>(3);
		this.navigationCheckBox = new ArrayList<JCheckBox>(3);
		initialize();
	}

	/**
	 * Return the contentPane property value.
	 * @return JPanel
	 */
	private JPanel getJContentPane() {
		if (contentPane == null) {
			contentPane = new JPanel();
			contentPane.setName("contentPane");
			contentPane.setLayout(
					new BoxLayout(contentPane,
							BoxLayout.Y_AXIS));

			contentPane.add(getNavigationPanel(0, "x"), null);
			contentPane.add(getNavigationPanel(1, "y"), null);
			contentPane.add(getNavigationPanel(2, "z"), null);

//			contentPane.add(getConnectButton());
		}
		return contentPane;
	}

	////////////////////////////////////////////////////////////////////////////
	//	array version
	////////////////////////////////////////////////////////////////////////////

	private JPanel getNavigationPanel(int index, String name) {
		if (navigationPanel.size() <= index || navigationPanel.get(index) == null) {
			navigationPanel.add(index, new JPanel());
			navigationPanel.get(index).setName("cursor"+name.toUpperCase()+"Panel");
			navigationPanel.get(index).setLayout(
					new BoxLayout(navigationPanel.get(index),
							BoxLayout.X_AXIS));

			navigationPanel.get(index).add(getNavigationLabel(index, name), null);
			navigationPanel.get(index).add(getNavigationSlider(index, name), null);
			navigationPanel.get(index).add(getNavigationTextField(index, name), null);
//			navigationPanel.get(index).add(getNavigationCheckBox(index, name), null);
		} else { System.out.println("navigationPanel["+String.valueOf(index)+"] was not null"); }
		
		return navigationPanel.get(index);
	}
	
	/**
	 * Return the cursorXLabel property value.
	 * @return JLabel
	 */
	private JLabel getNavigationLabel(int index, String name) {
		if (navigationLabel.size() <= index || navigationLabel.get(index) == null) {
			navigationLabel.add(index, new JLabel());
			navigationLabel.get(index).setName("navigation"+name.toUpperCase()+"Label");
			navigationLabel.get(index).setText(name);
			navigationLabel.get(index).setToolTipText("Rotation about "+name.toUpperCase()+" axis in degrees");
		}
		return navigationLabel.get(index);
	}
	
	/**
	 * Return the cursorXSlider property value.
	 * @return JSlider
	 */
	private JSlider getNavigationSlider(int index, String name) {
		if (navigationSlider.size() <= index || navigationSlider.get(index) == null) {
			navigationSlider.add(index, new JSlider());
			navigationSlider.get(index).setName("navigation"+name.toUpperCase()+"Slider");
			navigationSlider.get(index).setMinimum(-180);
			navigationSlider.get(index).setMaximum(180);
			navigationSlider.get(index).setMinorTickSpacing(30);
			navigationSlider.get(index).setPaintTicks(true);
			navigationSlider.get(index).addChangeListener(new IndexedSliderChangeListener(index));			
			navigationSlider.get(index).setToolTipText("Rotation about "+name.toUpperCase()+" axis in degrees");
			navigationSlider.get(index).setEnabled(true);
		}
		return navigationSlider.get(index);
	}
	
	/**
	 * Return the cursorXSpinner property value.
	 * @return JComboBox
	 */
	private JTextField getNavigationTextField(int index, String name) {
		if (navigationTextField.size() <= index || navigationTextField.get(index) == null) {

			navigationTextField.add(index, new JTextField());
			navigationTextField.get(index).setName("navigation"+name.toUpperCase()+"Spinner");
			navigationTextField.get(index).setColumns(5);
			navigationTextField.get(index).addActionListener(new IndexedTFActionListener(index));
			navigationTextField.get(index).addKeyListener(new IndexedSpinnerKeyAdapter(index));
			navigationTextField.get(index).setToolTipText("Rotation about "+name.toUpperCase()+" axis in degrees");
		}
		return navigationTextField.get(index);
	}

	private JCheckBox getNavigationCheckBox(int index, String name) {
		if (navigationCheckBox.size() <= index || navigationCheckBox.get(index) == null) {
			navigationCheckBox.add(index, new JCheckBox());
			navigationCheckBox.get(index).setName("navigation"+name.toUpperCase()+"CheckBox");
			navigationCheckBox.get(index).addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
							//	TODO
						}						
				});
			navigationCheckBox.get(index).setToolTipText("Enable "+name.toUpperCase()+" coordinate of the cursor");
		}
		return navigationCheckBox.get(index);		
	}

	////////////////////////////////////////////////////////////////////////////
	//	other elements
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Initialize the class.
	 */
	private void initialize() {
		this.setName("Navigation controls");
		this.setForeground(SystemColor.textHighlight);
		this.setModal(false);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setSize(240, 110);
		this.setTitle("Navigation controls");
		this.setContentPane(getJContentPane());
		this.setVisible(true);
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

	private JPanel contentPane = null;

	private ArrayList<JPanel> navigationPanel = null;
	private ArrayList<JLabel> navigationLabel = null;
	private ArrayList<JTextField> navigationTextField = null;
	private ArrayList<JSlider> navigationSlider = null;
	private ArrayList<JCheckBox> navigationCheckBox = null;
		
	private GridDisplay grid;

	private static final long serialVersionUID = 6682910773116076967L;
}
