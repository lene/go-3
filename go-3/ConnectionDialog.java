
/* Created on Feb 6, 2006 by helge
 */

/**
 @author helge
 
 TODO
 */

import java.awt.Frame;
import javax.swing.JButton;
/**
 * Example of components laid out in a grid
 */
public class ConnectionDialog extends javax.swing.JDialog {

	private javax.swing.JPanel contentPane = null;

	private javax.swing.JLabel serverHostLabel = null;
	private javax.swing.JTextField serverHostTextField = null;

	private javax.swing.JLabel serverPortLabel = null;
	private javax.swing.JTextField serverPortTextField = null;

	private javax.swing.JLabel usernameLabel = null;
	private javax.swing.JTextField usernameTextField = null;

	private javax.swing.JLabel sizeLabel = null;
	private javax.swing.JSlider sizeSlider = null;

	private javax.swing.JButton connectButton = null;

	private String serverHost = "";
	private int serverPort = 6666;
	private String username = "";
	private int boardSize = 3;
//	private GridDisplay parent;
	private ConnectionData connectionData;
	
	public ConnectionDialog(ConnectionData c) {
//		super((Frame)null, "", true);
		super ();
		this.connectionData = c;
		this.boardSize = c.getBoardSize();
		this.serverHost = c.getServerHost();
		this.serverPort = ConnectionData.getServerPort();
		this.username = c.getUsername();
//		this.parent = parent;
		
		initialize();
	}


	/**
	 * Return the JFrameContentPane property value.
	 * @return javax.swing.JPanel
	 */
	private javax.swing.JPanel getJContentPane() {
		if (contentPane == null) {
			contentPane = new javax.swing.JPanel();
			contentPane.setName("contentPane");
			contentPane.setLayout(
					new javax.swing.BoxLayout(contentPane,
							javax.swing.BoxLayout.Y_AXIS));
//			contentPane.setLayout(new java.awt.GridBagLayout());
			contentPane.add(getJLabel1(), null);
			contentPane.add(getJTextField1(), null);
			contentPane.add(getJLabel2());
			contentPane.add(getJTextField2(), null);
			contentPane.add(getJLabel3());
			contentPane.add(getJTextField3(), null);
			contentPane.add(getJLabel4(), null);
			contentPane.add(getJSlider1());
			
			contentPane.add(getJButton1());
		}
		return contentPane;
	}

	/**
	 * Return the JLabel1 property value.
	 * @return javax.swing.JLabel
	 */
	private javax.swing.JLabel getJLabel1() {
		if (serverHostLabel == null) {
			serverHostLabel = new javax.swing.JLabel();
			serverHostLabel.setName("serverHostLabel");
			serverHostLabel.setText("Server Host");
			serverHostLabel.setToolTipText("The address of the server.");
		}
		return serverHostLabel;
	}
	
	/**
	 * Return the JTextField1 property value.
	 * @return javax.swing.JTextField
	 */
	private javax.swing.JTextField getJTextField1() {
		if (serverHostTextField == null) {
			serverHostTextField = new javax.swing.JTextField();
			serverHostTextField.setName("serverHostTextField");
			serverHostTextField.setText(this.serverHost);
			serverHostTextField.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
					connectionData.setServerHost(serverHostTextField.getText());					
				}
			});
			serverHostTextField.setToolTipText("The address of the server.");
		}
		return serverHostTextField;
	}

	/**
	 * Return the JLabel2 property value.
	 * @return javax.swing.JLabel
	 */
	private javax.swing.JLabel getJLabel2() {
		if (serverPortLabel == null) {
			serverPortLabel = new javax.swing.JLabel();
			serverPortLabel.setName("serverPortLabel");
			serverPortLabel.setText("Server Port");
			serverPortLabel.setToolTipText("The port on which the " +
			"server listens. You usually shouldn't need to change this.");
		}
		return serverPortLabel;
	}

	/**
	 * Return the JTextField2 property value.
	 * @return javax.swing.JTextField
	 */
	private javax.swing.JTextField getJTextField2() {
		if (serverPortTextField == null) {
			serverPortTextField = new javax.swing.JTextField();
			serverPortTextField.setName("JTextField2");
			serverPortTextField.setText(String.valueOf(this.serverPort));
			serverPortTextField.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
					connectionData.setServerPort(Integer.valueOf(serverPortTextField.getText()).intValue());					
				}
			});
			serverPortTextField.setToolTipText("The port on which the " +
					"server listens. You usually shouldn't need to change this.");
			
		}
		return serverPortTextField;
	}

	/**
	 * Return the JLabel3 property value.
	 * @return javax.swing.JLabel
	 */
	private javax.swing.JLabel getJLabel3() {
		if (usernameLabel == null) {
			usernameLabel = new javax.swing.JLabel();
			usernameLabel.setName("JLabel3");
			usernameLabel.setText("Username");
			usernameLabel.setToolTipText("Enter the name under which you want" +
					" to connect to the server. You can only connect once" +
					" under any name.");
		}
		return usernameLabel;
	}

	/**
	 * Return the JTextField3 property value.
	 * @return javax.swing.JTextField
	 */
	private javax.swing.JTextField getJTextField3() {
		if (usernameTextField == null) {
			usernameTextField = new javax.swing.JTextField();
			usernameTextField.setName("JTextField3");
			usernameTextField.setText(this.username);
			usernameTextField.addKeyListener(new java.awt.event.KeyAdapter() {
				public void keyTyped(java.awt.event.KeyEvent e) {
					connectionData.setUsername(usernameTextField.getText());					
				}
			});
			usernameTextField.setToolTipText("Enter the name under which you want" +
					" to connect to the server. You can only connect once" +
					" under any name.");
		}
		return usernameTextField;
	}

	/**
	 * Return the JLabel2 property value.
	 * @return javax.swing.JLabel
	 */
	private javax.swing.JLabel getJLabel4() {
		if (sizeLabel == null) {
			sizeLabel = new javax.swing.JLabel();
			sizeLabel.setName("JLabel4");
			sizeLabel.setText("Board size: "+String.valueOf(this.boardSize)+"x"
					+String.valueOf(this.boardSize)+"x"+String.valueOf(this.boardSize));
			
			sizeLabel.setToolTipText("The size of the board on which you want" +
					" to play. This is not adjustable yet, you have to take" +
					" whichever size the server offers you.");
			sizeLabel.setEnabled (false);
		}
		return sizeLabel;
	}
	/**
	 * Return the JSlider1 property value.
	 * @return javax.swing.JSlider
	 */
	private javax.swing.JSlider getJSlider1() {
		if (sizeSlider == null) {
			sizeSlider = new javax.swing.JSlider();
			sizeSlider.setName("JSlider1");
			sizeSlider.setMinimum(3);
			sizeSlider.setMaximum(25);
			sizeSlider.setMinorTickSpacing(2);
			sizeSlider.setValue(this.boardSize);
			sizeSlider.addChangeListener(new javax.swing.event.ChangeListener() {
				public void stateChanged(javax.swing.event.ChangeEvent e) {
					String boardSizeString = String.valueOf(sizeSlider.getValue());
					sizeLabel.setText("Board Size: "+boardSizeString+"x"
							+boardSizeString+"x"+boardSizeString);
					connectionData.setBoardSize(sizeSlider.getValue());
				}
			});
			
			sizeSlider.setToolTipText("The size of the board on which you want" +
					" to play. This is not adjustable yet, you have to take" +
					" whichever size the server offers you.");
			sizeSlider.setEnabled(false);
		}
		return sizeSlider;
	}

	/**
	 * Return the JButton1 property value.
	 * @return javax.swing.JButton
	 */
	private javax.swing.JButton getJButton1() {
		if (connectButton == null) {
			connectButton = new javax.swing.JButton();
			connectButton.setName("JButton1");
			connectButton.setText("Connect to Server");
			connectButton.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					if (e.getActionCommand().equals (connectButton.getText())) {
						System.out.println("actionPerformed(): "+e.getActionCommand()); // TODO Auto-generated Event stub actionPerformed()
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

		this.setName("JFrame1");
		this.setForeground(java.awt.SystemColor.textHighlight);
		this.setModal(true);
		this.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
		this.setSize(206, 217);
		this.setTitle("Connect to Go Server");
		this.setContentPane(getJContentPane());

	}
	static public void main (String args[]) {
		new ConnectionDialog (new ConnectionData ()).setVisible(true);
	}
}  //  @jve:decl-index=0:visual-constraint="10,10"
