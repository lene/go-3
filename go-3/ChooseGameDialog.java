
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;

public class ChooseGameDialog extends JDialog {

	class GameData {
		private String name;
		private int size;
		
		GameData(String inputLine) throws NumberFormatException {
			name = Utility.getArg (inputLine, 1);
			size = Integer.parseInt (Utility.getArg (inputLine, 2));
		}
		public String toString() { 
			return name+" ("+size+"x"+size+"x"+size+")"; }
		public String getName() { return name; }
		public int getSize() { return size;	}
	}
	
	public ChooseGameDialog(ConnectedPlayer p, ConnectionData c) {
		super ();
		this.connectionData = c;
		this.player = p;
		
		initialize();
	}
	

	GameData[] gameData = {};
	
	GameData[] getGames() {
		player.out.println("game list");
		ArrayList<GameData> games = new ArrayList<GameData>();
		
		try {
			String input = "";
			while (!input.startsWith("game list")) {
				input = player.in.readLine();
				Utility.debug (input);
				if (!input.startsWith("game list")) 
					games.add(new GameData(input));
			}
		} catch (IOException e) { Utility.debug("IOException!"); }		//	TODO
	
		gameData = games.toArray(gameData);
		return gameData;
	}

	/**
	 * Return the JFrameContentPane property value.
	 * @return javax.swing.JPanel
	 */
	private JPanel getJContentPane() {
		if (contentPane == null) {
			contentPane = new JPanel();
			contentPane.setName("contentPane");
			contentPane.setLayout(
					new javax.swing.BoxLayout(contentPane,
							javax.swing.BoxLayout.Y_AXIS));

			contentPane.add(getJLabel1(), null);
			contentPane.add(getJList1(), null);
			
			contentPane.add(getJButton1());
		}
		return contentPane;
	}

	/**
	 * Return the JLabel1 property value.
	 * @return javax.swing.JLabel
	 */
	private javax.swing.JLabel getJLabel1() {
		if (gameListLabel == null) {
			gameListLabel = new javax.swing.JLabel();
			gameListLabel.setName("gameListLabel");
			gameListLabel.setText("Choose a Game");
			gameListLabel.setToolTipText("The games in the list are currently " +
					"running on the server and waiting for a partner. " +
					"Choose one to join.");
		}
		return gameListLabel;
	}
	

	private JList getJList1() {
		if (gameList == null) {
			gameList = new JList(getGames());

	        gameList.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
	        gameList.setLayoutOrientation(JList.VERTICAL);
	        gameList.setVisibleRowCount(-1);

	        JScrollPane listScroller = new JScrollPane(gameList);
//	        listScroller.setPreferredSize(new Dimension(250, 80));
	        listScroller.setAlignmentX(LEFT_ALIGNMENT);
	    }
		return gameList;
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
			connectButton.addActionListener(
					new java.awt.event.ActionListener() {
						public void actionPerformed(java.awt.event.ActionEvent e) {
							if (e.getActionCommand().equals (connectButton.getText())) {
								int index = gameList.getSelectedIndex();
								
								if (index >= 0 && index < gameData.length) {
									Utility.debug(index+" "+gameData[index]);
									connectionData.setGame(gameData[index].getName());
									connectionData.setBoardSize(gameData[index].getSize());
									
									setVisible (false);
								}
							}
						}
					});
		}
		return connectButton;
	}

	/**
	 * This method initializes this
	 * 
	 */
	private void initialize() {
        this.setName("Game Chooser Dialog");
		this.setForeground(java.awt.SystemColor.textHighlight);
		this.setModal(true);
		this.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
		this.setSize(320, 240);
		this.setTitle("Choose a Game");
		this.setContentPane(getJContentPane());
	}
	
	private ConnectionData connectionData;
	private ConnectedPlayer player;
	
	private JPanel contentPane = null;

	private JLabel gameListLabel = null;
	private JList gameList = null;
	
	private JButton connectButton = null;

	public static void main(String[] args) {
//		new ChooseGameDialog(new ConnectionData()).setVisible(true);
	}

}  //  @jve:decl-index=0:visual-constraint="10,10"
