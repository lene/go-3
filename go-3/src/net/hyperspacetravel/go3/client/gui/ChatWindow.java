/**
 * 
 */
package net.hyperspacetravel.go3.client.gui;

import java.awt.SystemColor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

import net.hyperspacetravel.go3.GoGrid;
import net.hyperspacetravel.go3.client.ChatListener;

/**
 * @author helge
 *
 */
public class ChatWindow extends JDialog implements ChatListener {

	public ChatWindow() {
		initialize();
	}

	public ChatWindow(GoGrid _grid) {
		this.grid = _grid;
		initialize();
	}
	
	public void setGrid(GoGrid _grid) {
		this.grid = _grid;
	}
	
	public void message() {
		// TODO Auto-generated method stub
		
	}	
	/* (non-Javadoc)
	 * @see net.hyperspacetravel.go3.client.ChatListener#message()
	 */
	public void sendMessage() {
		// TODO Auto-generated method stub
		grid.sendMessage(0, "Implement me: ChatWindow.sendMessage()");
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

			contentPane.add(getInputTextField(), null);
			contentPane.add(getOutputTextArea(), null);
		}
		return contentPane;
	}

	private JTextField getInputTextField() {
		if (inputTextField == null) {
			inputTextField = new JTextField();
			inputTextField.setName("inputTextField");
			inputTextField.setText("Type your messages here");
			inputTextField.addKeyListener(new KeyAdapter() {
				public void keyTyped(KeyEvent e) {
//					inputTextField.setText(inputTextField.getText()+e.getKeyChar());					
				}
			});
			inputTextField.setToolTipText("Enter the name under which you want" +
					" to connect to the server. You can only connect once" +
					" under any name.");

		}
		return inputTextField;
	}

	private JTextArea getOutputTextArea() {
		if (outputTextArea == null) {
			outputTextArea = new JTextArea("blah...", 6, 40);
		}
		return outputTextArea;
	}

	/**
	 * This method initializes this
	 * 
	 */
	private void initialize() {
        this.setName("Chat Window");
		this.setForeground(SystemColor.textHighlight);
		this.setModal(false);
		this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.setSize(300, 130);
		this.setTitle("Chat Window");
		this.setContentPane(getJContentPane());
		this.setVisible(true);
	}
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////	

	private JPanel contentPane = null;

	private JTextField inputTextField = null;
	private JTextArea outputTextArea = null;
	
	private GoGrid grid;
	/**														 */
	private static final long serialVersionUID = 4643374556829922269L;


}
