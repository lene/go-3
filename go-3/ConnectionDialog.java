
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

	public ConnectionDialog(int s, String h, int p, String u) {
		super((Frame)null, "", true);
		this.boardSize = s;
		this.serverHost = h;
		this.serverPort = p;
		this.username = u;
		
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
			contentPane.setLayout(new javax.swing.BoxLayout(contentPane,
					javax.swing.BoxLayout.Y_AXIS));
//			contentPane.setLayout(new java.awt.GridBagLayout());
			contentPane.add(getJLabel1(), null);
			contentPane.add(getJTextField1(), null);
			getContentPane().add(getJLabel1());
			getContentPane().add(getJTextField1());
			contentPane.add(getJLabel2());
			contentPane.add(getJTextField2(), null);
			getContentPane().add(getJTextField2());
			contentPane.add(getJLabel3());
			contentPane.add(getJTextField3(), null);
			contentPane.add(getJLabel4(), null);
			getContentPane().add(getJTextField3());
			
			getContentPane().add(getJLabel4());
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
			serverHostTextField.setName("JTextField1");
			serverHostTextField.setText(this.serverHost);
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
			serverPortLabel.setName("JLabel2");
			serverPortLabel.setText("Server Port");
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
			sizeLabel.setText("Board size");
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
		this.setSize(250, 182);
		this.setTitle("Connect to Go Server");
		this.setContentPane(getJContentPane());

	}
}  //  @jve:decl-index=0:visual-constraint="10,10"
