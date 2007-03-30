/**
 * 
 */
package net.hyperspacetravel.go3.client.gui;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;

import javax.swing.Box;
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

import java.util.ArrayList;

import net.hyperspacetravel.go3.Utility;
import net.hyperspacetravel.go3.client.CursorListener;

/**
 * @author helge
 *
 */
public class CursorDialog extends JDialog implements CursorListener {
	
	class IndexedChangeListener implements ChangeListener {
		IndexedChangeListener(int i) {
			this.index = i;
		}
		
		public void stateChanged(ChangeEvent arg0) {
			
		}
		
		protected int index = 0;
	}
	
	class IndexedSliderChangeListener extends IndexedChangeListener {
		
		IndexedSliderChangeListener(int i) { super(i); }
		
		public void stateChanged(ChangeEvent arg0) {
			grid.setCursor(cursorSlider.get(0).getValue(), 
						   cursorSlider.get(1).getValue(), 
						   cursorSlider.get(2).getValue());
			cursorSpinner.get(index).setValue(cursorSlider.get(index).getValue());
			libertiesDisplay.setText("");
		}
		
	}
	
	class IndexedSpinnerChangeListener extends IndexedChangeListener {

		IndexedSpinnerChangeListener(int i) { super(i);	}
		
		public void stateChanged(ChangeEvent arg0) {
			SpinnerModel model = cursorSpinner.get(index).getModel();
	        if (model instanceof SpinnerNumberModel) {
	        	cursorSlider.get(index).setValue(((SpinnerNumberModel)model).getNumber().intValue());
	    		libertiesDisplay.setText("");
	        }
		}						
	}

	class IndexedSpinnerKeyAdapter extends KeyAdapter {

		IndexedSpinnerKeyAdapter(int i) {
			this.index = i;
		}
		
		public void keyTyped(java.awt.event.KeyEvent e) {
			SpinnerModel model = cursorSpinner.get(index).getModel();
	        if (model instanceof SpinnerNumberModel) {
	        	cursorSlider.get(index).setValue(((SpinnerNumberModel)model).getNumber().intValue());
	    		libertiesDisplay.setText("");
	        }
		}
		
		int index = 0;
	}

	public CursorDialog(GridDisplay _grid) {
		this.grid = _grid;
		initialize();
	}

	public CursorDialog(GridDisplay _grid, Frame frame) {
		super(frame);
		this.grid = _grid;
		initialize();
	}

	/* (non-Javadoc)
	 * @see net.hyperspacetravel.go3.client.CursorListener#notifyCursor(int, int, int)
	 */
	public void notifyCursor(int x, int y, int z) {
		cursorSpinner.get(0).setValue(x);
		cursorSlider.get(0).setValue(x);
		cursorSpinner.get(1).setValue(y);
		cursorSlider.get(1).setValue(y);
		cursorSpinner.get(2).setValue(z);
		cursorSlider.get(2).setValue(z);
		libertiesDisplay.setText("");
	}
	
	/* (non-Javadoc)
	 * @see net.hyperspacetravel.go3.client.CursorListener#activate(boolean)
	 */
	public void activate(boolean state) {
		setButton.setEnabled(state);
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

			contentPane.add(getCursorPanel(0, "x"), null);
			contentPane.add(getCursorPanel(1, "y"), null);
			contentPane.add(getCursorPanel(2, "z"), null);

			contentPane.add(getLibertiesPanel(), null);
			
			contentPane.add(getSetButton());
		}
		return contentPane;
	}

	////////////////////////////////////////////////////////////////////////////
	//	cursor controls
	////////////////////////////////////////////////////////////////////////////

	private JPanel getCursorPanel(int index, String name) {
		if (cursorPanel.size() <= index || cursorPanel.get(index) == null) {
			cursorPanel.add(index, new JPanel());
			cursorPanel.get(index).setName("cursor"+name.toUpperCase()+"Panel");
			cursorPanel.get(index).setLayout(
					new BoxLayout(cursorPanel.get(index),
							BoxLayout.X_AXIS));

			cursorPanel.get(index).add(getCursorLabel(index, name), null);
			cursorPanel.get(index).add(getCursorSlider(index, name), null);
			cursorPanel.get(index).add(getCursorSpinner(index, name), null);
			cursorPanel.get(index).add(getCursorCheckBox(index, name), null);
		} else { System.out.println("cursorPanel["+String.valueOf(index)+"] was not null"); }
		
		return cursorPanel.get(index);
	}
	
	/**
	 * Return the cursorXLabel property value.
	 * @return JLabel
	 */
	private JLabel getCursorLabel(int index, String name) {
		if (cursorLabel.size() <= index || cursorLabel.get(index) == null) {
			cursorLabel.add(index, new JLabel());
			cursorLabel.get(index).setName("cursor"+name.toUpperCase()+"Label");
			cursorLabel.get(index).setText(name);
			cursorLabel.get(index).setToolTipText(name.toUpperCase()+" coordinate of the cursor");
		}
		return cursorLabel.get(index);
	}
	
	/**
	 * Return the cursorXSlider property value.
	 * @return JSlider
	 */
	private JSlider getCursorSlider(int index, String name) {
		if (cursorSlider.size() <= index || cursorSlider.get(index) == null) {
			cursorSlider.add(index, new JSlider());
			cursorSlider.get(index).setName("cursor"+name.toUpperCase()+"Slider");
			cursorSlider.get(index).setMinimum(1);
			cursorSlider.get(index).setMaximum(this.grid.getBoardSize());
			cursorSlider.get(index).setMinorTickSpacing(1);
			cursorSlider.get(index).setValue((this.grid.getBoardSize()+1)/2);
			cursorSlider.get(index).setPaintTicks(true);
			cursorSlider.get(index).setPaintLabels(true);
			cursorSlider.get(index).addChangeListener(new IndexedSliderChangeListener(index));			
			cursorSlider.get(index).setToolTipText(name.toUpperCase()+" coordinate of the cursor");
			cursorSlider.get(index).setEnabled(true);
		}
		return cursorSlider.get(index);
	}
	
	/**
	 * Return the cursorXSpinner property value.
	 * @return JComboBox
	 */
	private JSpinner getCursorSpinner(int index, String name) {
		if (cursorSpinner.size() <= index || cursorSpinner.get(index) == null) {
	        SpinnerModel model =
                new SpinnerNumberModel((this.grid.getBoardSize()+1)/2, 			//	initial value
                                       1, this.grid.getBoardSize(), 1);			//	min, max, step

			cursorSpinner.add(index, new JSpinner(model));
			cursorSpinner.get(index).setName("cursor"+name.toUpperCase()+"Spinner");
			cursorSpinner.get(index).addChangeListener(new IndexedSpinnerChangeListener(index));
			cursorSpinner.get(index).addKeyListener(new IndexedSpinnerKeyAdapter(index));
			cursorSpinner.get(index).setToolTipText(name.toUpperCase()+" coordinate of the cursor");
		}
		return cursorSpinner.get(index);
	}

	private JCheckBox getCursorCheckBox(int index, String name) {
		if (cursorCheckBox.size() <= index || cursorCheckBox.get(index) == null) {
			cursorCheckBox.add(index, new JCheckBox());
			cursorCheckBox.get(index).setName("cursor"+name.toUpperCase()+"CheckBox");
			cursorCheckBox.get(index).addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
							//	TODO
						}						
				});
			cursorCheckBox.get(index).setToolTipText("Enable "+name.toUpperCase()+" coordinate of the cursor");
		}
		return cursorCheckBox.get(index);		
	}

	////////////////////////////////////////////////////////////////////////////
	//	other elements
	////////////////////////////////////////////////////////////////////////////

	private JPanel getLibertiesPanel() {
		if (libertiesPanel == null) {
			libertiesPanel = new JPanel();
			libertiesPanel.setName("libertiesPanel");
			libertiesPanel.setLayout(
					new BoxLayout(libertiesPanel,
							BoxLayout.X_AXIS));

			libertiesPanel.add(getLibertiesLabel(), null);
			libertiesPanel.add(Box.createHorizontalGlue());
			libertiesPanel.add(getLibertiesButton(), null);
			libertiesPanel.add(Box.createHorizontalGlue());
			libertiesPanel.add(getLibertiesDisplay(), null);
		}
		
		return libertiesPanel;
	}

	public JLabel getLibertiesLabel() {
		if (libertiesLabel == null) {
			libertiesLabel = new JLabel();
			libertiesLabel.setName("libertiesLabel");
			libertiesLabel.setText("Liberties:");
			libertiesLabel.setToolTipText("Display liberties at current cursor position");
		}
		return libertiesLabel;
	}

	public JButton getLibertiesButton() {
		if (libertiesButton == null) {
			libertiesButton = new JButton();
			libertiesButton.setName("libertiesButton");
			libertiesButton.setText("Show");
			libertiesButton.setToolTipText("Display liberties at current cursor position");
			libertiesButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if (e.getActionCommand().equals (libertiesButton.getText())) {
						Integer liberties = grid.Liberty(cursorSlider.get(0).getValue(),
														 cursorSlider.get(1).getValue(),
														 cursorSlider.get(2).getValue(),
														 grid.getCurrentPlayer(),
														 false);
						libertiesDisplay.setText(liberties.toString());
					}
				}
			});

		}
		return libertiesButton;
	}

	public JLabel getLibertiesDisplay() {
		if (libertiesDisplay == null) {
			libertiesDisplay = new JLabel();
			libertiesDisplay.setName("libertiesDisplay");
			libertiesDisplay.setText("");
			libertiesDisplay.setToolTipText("Liberties at current cursor position");
			libertiesDisplay.setMinimumSize(new Dimension(40, 16));
		}
		return libertiesDisplay;
	}
	
	/**
	 * Return the setButton property value.
	 * @return JButton
	 */
	private JButton getSetButton() {
		if (setButton == null) {
			setButton = new JButton();
			setButton.setName("setButton");
			setButton.setText("Set");
			setButton.setToolTipText("Set stone at current cursor position");
			setButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					if (e.getActionCommand().equals (setButton.getText())) {
						grid.setStone();
					}
				}
			});
		}
		return setButton;
	}

	/**
	 * Initialize the class.
	 */
	private void initialize() {
		this.cursorPanel = new ArrayList<JPanel>(3);
		this.cursorLabel = new ArrayList<JLabel>(3);
		this.cursorSpinner = new ArrayList<JSpinner>(3);
		this.cursorSlider = new ArrayList<JSlider>(3);
		this.cursorCheckBox = new ArrayList<JCheckBox>(3);

		this.setName("Cursor controls");
		this.setForeground(java.awt.SystemColor.textHighlight);
		this.setModal(false);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setSize(240, 160);
		this.setTitle("Cursor controls");
		this.setContentPane(getJContentPane());
		this.setVisible(true);
	}

	
	private JPanel contentPane = null;

	private ArrayList<JPanel> cursorPanel = null;
	private ArrayList<JLabel> cursorLabel = null;
	private ArrayList<JSpinner> cursorSpinner = null;
	private ArrayList<JSlider> cursorSlider = null;
	private ArrayList<JCheckBox>cursorCheckBox = null;
	
	private JPanel libertiesPanel = null;
	private JLabel libertiesLabel = null;
	private JButton libertiesButton = null;
	private JLabel libertiesDisplay = null;

	private JButton setButton = null;

	
	private GridDisplay grid;

	/**														 */
	private static final long serialVersionUID = 1590600506585137608L;


}
