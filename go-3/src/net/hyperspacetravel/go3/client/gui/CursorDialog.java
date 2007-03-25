/**
 * 
 */
package net.hyperspacetravel.go3.client.gui;

import java.awt.Frame;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.hyperspacetravel.go3.GoGrid;
import net.hyperspacetravel.go3.Utility;
import net.hyperspacetravel.go3.client.CursorListener;

/**
 * @author helge
 *
 */
public class CursorDialog extends JDialog implements CursorListener {

	/* (non-Javadoc)
	 * @see net.hyperspacetravel.go3.client.CursorListener#notifyCursor(int, int, int)
	 */
	public void notifyCursor(int x, int y, int z) {
		// TODO Auto-generated method stub

	}
	
	
	public CursorDialog(GoGrid _grid, Frame frame) {
		super(frame);
		this.grid = _grid; 
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

			contentPane.add(getCursorXPanel(), null);
			contentPane.add(getCursorYPanel(), null);
			contentPane.add(getCursorZPanel(), null);

//			contentPane.add(getConnectButton());
		}
		return contentPane;
	}

	////////////////////////////////////////////////////////////////////////////
	//	X coordinate
	////////////////////////////////////////////////////////////////////////////

	private JPanel getCursorXPanel() {
		if (cursorXPanel == null) {
			cursorXPanel = new JPanel();
			cursorXPanel.setName("cursorXPanel");
			cursorXPanel.setLayout(
					new BoxLayout(cursorXPanel,
							BoxLayout.X_AXIS));

			cursorXPanel.add(getCursorXLabel(), null);
			cursorXPanel.add(getCursorXSlider(), null);
			cursorXPanel.add(getCursorXSpinner(), null);
			cursorXPanel.add(getCursorXCheckBox(), null);
		}
		
		return cursorXPanel;
	}

	/**
	 * Return the cursorXLabel property value.
	 * @return JLabel
	 */
	private JLabel getCursorXLabel() {
		if (cursorXLabel == null) {
			cursorXLabel = new JLabel();
			cursorXLabel.setName("cursorXLabel");
			cursorXLabel.setText("x");
			cursorXLabel.setToolTipText("X coordinate of the cursor");
		}
		return cursorXLabel;
	}
	
	/**
	 * Return the cursorXSlider property value.
	 * @return JSlider
	 */
	private JSlider getCursorXSlider() {
		if (cursorXSlider == null) {
			cursorXSlider = new JSlider();
			cursorXSlider.setName("cursorXSlider");
			cursorXSlider.setMinimum(1);
			cursorXSlider.setMaximum(this.grid.getBoardSize());
			cursorXSlider.setMinorTickSpacing(1);
			cursorXSlider.setValue((this.grid.getBoardSize()+1)/2);
			cursorXSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					grid.setCursor(cursorXSlider.getValue(), 
										cursorYSlider.getValue(), 
										cursorZSlider.getValue());
					cursorXSpinner.setValue(cursorXSlider.getValue());
				}
			});
			
			cursorXSlider.setToolTipText("X coordinate of the cursor");
			cursorXSlider.setEnabled(true);
		}
		return cursorXSlider;
	}
	
	/**
	 * Return the cursorXSpinner property value.
	 * @return JComboBox
	 */
	private JSpinner getCursorXSpinner() {
		if (cursorXSpinner == null) {
	        SpinnerModel model =
                new SpinnerNumberModel((this.grid.getBoardSize()+1)/2, 			//	initial value
                                       1, this.grid.getBoardSize(), 1);			//	min, max, step


			cursorXSpinner = new JSpinner(model);
			cursorXSpinner.setName("cursorXSpinner");
			cursorXSpinner.addChangeListener(
				new ChangeListener() {
					public void stateChanged(ChangeEvent arg0) {
						SpinnerModel model = cursorXSpinner.getModel();
				        if (model instanceof SpinnerNumberModel) {
				        	cursorXSlider.setValue(((SpinnerNumberModel)model).getNumber().intValue());
				        }
					}						
				});
			cursorXSpinner.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
					SpinnerModel model = cursorXSpinner.getModel();
			        if (model instanceof SpinnerNumberModel) {
			        	cursorXSlider.setValue(((SpinnerNumberModel)model).getNumber().intValue());
			        }
				}
			});
			cursorXSpinner.setToolTipText("X coordinate of the cursor");
		}
		return cursorXSpinner;
	}

	private JCheckBox getCursorXCheckBox() {
		if (cursorXCheckBox == null) {
			cursorXCheckBox = new JCheckBox();
			cursorXCheckBox.setName("cursorXCheckBox");
			cursorXCheckBox.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
							//	TODO
						}						
				});
			cursorXCheckBox.setToolTipText("Enable X coordinate of the cursor");
		}
		return cursorXCheckBox;		
	}

	////////////////////////////////////////////////////////////////////////////
	//	Y coordinate
	////////////////////////////////////////////////////////////////////////////
	
	private JPanel getCursorYPanel() {
		if (cursorYPanel == null) {
			cursorYPanel = new JPanel();
			cursorYPanel.setName("cursorYPanel");
			cursorYPanel.setLayout(
					new BoxLayout(cursorYPanel,
							BoxLayout.X_AXIS));

			cursorYPanel.add(getCursorYLabel(), null);
			cursorYPanel.add(getCursorYSlider(), null);
			cursorYPanel.add(getCursorYSpinner(), null);
			cursorYPanel.add(getCursorYCheckBox(), null);
		}
		
		return cursorYPanel;
	}

	/**
	 * Return the cursorYLabel property value.
	 * @return JLabel
	 */
	private JLabel getCursorYLabel() {
		if (cursorYLabel == null) {
			cursorYLabel = new JLabel();
			cursorYLabel.setName("cursorYLabel");
			cursorYLabel.setText("y");
			cursorYLabel.setToolTipText("Y coordinate of the cursor");
		}
		return cursorYLabel;
	}
	
	/**
	 * Return the cursorYSlider property value.
	 * @return JSlider
	 */
	private JSlider getCursorYSlider() {
		if (cursorYSlider == null) {
			cursorYSlider = new JSlider();
			cursorYSlider.setName("cursorYSlider");
			cursorYSlider.setMinimum(1);
			cursorYSlider.setMaximum(this.grid.getBoardSize());
			cursorYSlider.setMinorTickSpacing(1);
			cursorYSlider.setValue((this.grid.getBoardSize()+1)/2);
			cursorYSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					grid.setCursor(cursorXSlider.getValue(), 
								   cursorYSlider.getValue(), 
								   cursorZSlider.getValue());
					cursorYSpinner.setValue(cursorYSlider.getValue());
				}
			});
			
			cursorYSlider.setToolTipText("Y coordinate of the cursor");
			cursorYSlider.setEnabled(true);
		}
		return cursorYSlider;
	}
	
	/**
	 * Return the cursorYSpinner property value.
	 * @return JComboBox
	 */
	private JSpinner getCursorYSpinner() {
		if (cursorYSpinner == null) {
	        SpinnerModel model =
                new SpinnerNumberModel((this.grid.getBoardSize()+1)/2, 			//	initial value
                                       1, this.grid.getBoardSize(), 1);			//	min, max, step


			cursorYSpinner = new JSpinner(model);
			cursorYSpinner.setName("cursorYSpinner");
			cursorYSpinner.addChangeListener(
				new ChangeListener() {
					public void stateChanged(ChangeEvent arg0) {
						SpinnerModel model = cursorYSpinner.getModel();
				        if (model instanceof SpinnerNumberModel) {
				        	cursorYSlider.setValue(((SpinnerNumberModel)model).getNumber().intValue());
				        }
					}						
				});
			cursorYSpinner.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
					SpinnerModel model = cursorYSpinner.getModel();
			        if (model instanceof SpinnerNumberModel) {
			        	cursorYSlider.setValue(((SpinnerNumberModel)model).getNumber().intValue());
			        }
				}
			});
			cursorYSpinner.setToolTipText("Y coordinate of the cursor");
		}
		return cursorYSpinner;
	}

	private JCheckBox getCursorYCheckBox() {
		if (cursorYCheckBox == null) {
			cursorYCheckBox = new JCheckBox();
			cursorYCheckBox.setName("cursorYCheckBox");
			cursorYCheckBox.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
							//	TODO
						}						
				});
			cursorYCheckBox.setToolTipText("Enable Y coordinate of the cursor");
		}
		return cursorYCheckBox;		
	}

	////////////////////////////////////////////////////////////////////////////
	//	Z coordinate
	////////////////////////////////////////////////////////////////////////////

	private JPanel getCursorZPanel() {
		if (cursorZPanel == null) {
			cursorZPanel = new JPanel();
			cursorZPanel.setName("cursorZPanel");
			cursorZPanel.setLayout(
					new BoxLayout(cursorZPanel,
							BoxLayout.X_AXIS));

			cursorZPanel.add(getCursorZLabel(), null);
			cursorZPanel.add(getCursorZSlider(), null);
			cursorZPanel.add(getCursorZSpinner(), null);
			cursorZPanel.add(getCursorZCheckBox(), null);
		}
		
		return cursorZPanel;
	}

	/**
	 * Return the cursorZLabel property value.
	 * @return JLabel
	 */
	private JLabel getCursorZLabel() {
		if (cursorZLabel == null) {
			cursorZLabel = new JLabel();
			cursorZLabel.setName("cursorZLabel");
			cursorZLabel.setText("z");
			cursorZLabel.setToolTipText("Z coordinate of the cursor");
		}
		return cursorZLabel;
	}
	
	/**
	 * Return the cursorZSlider property value.
	 * @return JSlider
	 */
	private JSlider getCursorZSlider() {
		if (cursorZSlider == null) {
			cursorZSlider = new JSlider();
			cursorZSlider.setName("cursorZSlider");
			cursorZSlider.setMinimum(1);
			cursorZSlider.setMaximum(this.grid.getBoardSize());
			cursorZSlider.setMinorTickSpacing(1);
			cursorZSlider.setValue((this.grid.getBoardSize()+1)/2);
			cursorZSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					grid.setCursor(cursorXSlider.getValue(), 
								   cursorYSlider.getValue(), 
								   cursorZSlider.getValue());
					cursorZSpinner.setValue(cursorZSlider.getValue());
				}
			});
			
			cursorZSlider.setToolTipText("Z coordinate of the cursor");
			cursorZSlider.setEnabled(true);
		}
		return cursorZSlider;
	}
	
	/**
	 * Return the cursorZSpinner property value.
	 * @return JComboBox
	 */
	private JSpinner getCursorZSpinner() {
		if (cursorZSpinner == null) {
	        SpinnerModel model =
                new SpinnerNumberModel((this.grid.getBoardSize()+1)/2, 			//	initial value
                                       1, this.grid.getBoardSize(), 1);			//	min, max, step


			cursorZSpinner = new JSpinner(model);
			cursorZSpinner.setName("cursorZSpinner");
			cursorZSpinner.addChangeListener(
				new ChangeListener() {
					public void stateChanged(ChangeEvent arg0) {
						SpinnerModel model = cursorZSpinner.getModel();
				        if (model instanceof SpinnerNumberModel) {
				        	cursorZSlider.setValue(((SpinnerNumberModel)model).getNumber().intValue());
				        }
					}						
				});
			cursorZSpinner.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
					SpinnerModel model = cursorZSpinner.getModel();
			        if (model instanceof SpinnerNumberModel) {
			        	cursorZSlider.setValue(((SpinnerNumberModel)model).getNumber().intValue());
			        }
				}
			});
			cursorZSpinner.setToolTipText("Z coordinate of the cursor");
		}
		return cursorZSpinner;
	}

	private JCheckBox getCursorZCheckBox() {
		if (cursorZCheckBox == null) {
			cursorZCheckBox = new JCheckBox();
			cursorZCheckBox.setName("cursorZCheckBox");
			cursorZCheckBox.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
							//	TODO
						}						
				});
			cursorZCheckBox.setToolTipText("Enable Z coordinate of the cursor");
		}
		return cursorZCheckBox;		
	}

	////////////////////////////////////////////////////////////////////////////
	//	other elements
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Return the connectButton property value.
	 * @return JButton
	 */
	private JButton getConnectButton() {
		if (connectButton == null) {
			connectButton = new JButton();
			connectButton.setName("connectButton");
			connectButton.setText("Connect to Server");
			connectButton.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					if (e.getActionCommand().equals (connectButton.getText())) {
						Utility.debug("actionPerformed(): "+e.getActionCommand()); // TODO Auto-generated Event stub actionPerformed()
						setVisible (false);
						
					}
				}
			});
		}
		return connectButton;
	}

	/**
	 * Initialize the class.
	 */
	private void initialize() {
		System.out.println("CursorDialog.initialize()");
		this.setName("Cursor controls");
		this.setForeground(java.awt.SystemColor.textHighlight);
//		this.setModal(false);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setSize(240, 170);
		this.setTitle("Cursor controls");
		this.setContentPane(getJContentPane());
		this.setVisible(true);
	}

	
	private JPanel contentPane = null;

	private JPanel cursorXPanel = null;
	private JLabel cursorXLabel = null;
	private JSpinner cursorXSpinner = null;
	private JSlider cursorXSlider = null;
	private JCheckBox cursorXCheckBox = null;
	
	private JPanel cursorYPanel = null;
	private JLabel cursorYLabel = null;
	private JSpinner cursorYSpinner = null;
	private JSlider cursorYSlider = null;
	private JCheckBox cursorYCheckBox = null;
	
	private JPanel cursorZPanel = null;
	private JLabel cursorZLabel = null;
	private JSpinner cursorZSpinner = null;
	private JSlider cursorZSlider = null;
	private JCheckBox cursorZCheckBox = null;
	
	private JButton connectButton = null;

	
	private GoGrid grid;

	/**														 */
	private static final long serialVersionUID = 1590600506585137608L;
}
