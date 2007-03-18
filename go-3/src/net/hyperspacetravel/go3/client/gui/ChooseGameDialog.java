package net.hyperspacetravel.go3.client.gui;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.hyperspacetravel.go3.ConnectedPlayer;
import net.hyperspacetravel.go3.ConnectionData;
import net.hyperspacetravel.go3.Utility;
import net.hyperspacetravel.go3.GameBase;


import java.awt.event.ItemEvent;
import java.io.IOException;
import java.util.*;

public class ChooseGameDialog extends JDialog {

	class GameData implements Comparable {
		private String name;
		private int size;
		
		GameData(String inputLine) throws NumberFormatException {
			name = Utility.getArg (inputLine, 1);
			size = Integer.parseInt (Utility.getArg (inputLine, 2));
		}
		public String toString() { 
			return ""+name+"("+size+"x"+size+"x"+size+")"; }
		public String getName() { return name; }
		public int getSize() { return size;	}
		public int compareTo(Object arg0) {
			return (toString().compareTo(arg0.toString()));
		}
	}
	
	class UpdateThread extends Thread {
		private int delay;
		public UpdateThread(int d) { delay = d; }
		public void run() {
			while (!stopped) {
				try { sleep (delay); } catch (InterruptedException e) { return; }
				updateGameList();
			}
		}
	}
	
	public ChooseGameDialog(ConnectedPlayer p, ConnectionData c) {
		super ();
		this.connectionData = c;
		this.player = p;
		
		initialize();
	}
		
	ArrayList<GameData> getGames() {
		player.out.println("game list");
		ArrayList<GameData> games = new ArrayList<GameData>();
		
		try {
			String input = "";
			while (!input.startsWith("game list")) {
				input = player.in.readLine();
				if (!input.startsWith("game list")) 
					games.add(new GameData(input));
			}
			Collections.sort(games);
		} 
		catch (IOException e) { Utility.debug("IOException!"); }		//	TODO
		catch (NumberFormatException e) { Utility.debug("NumberFormatException!"); }
	
		return games;
	}
	
	GameData[] setGameList() {
		gameData = getGames().toArray(gameData);
		return gameData;		
	}
	
	void updateGameList () {
		ArrayList<GameData> tempData = getGames();
		if (tempData.equals(gameData)) return;				//	nothing changed
		
		int index = pendingList.getSelectedIndex(); //get selected index
		model.clear();
		for (int i = 0; i < tempData.size(); i++) model.addElement(tempData.get(i));
	    pendingList.setSelectedIndex(index);
	    pendingList.ensureIndexIsVisible(index);
	}

	//	UI generation methods

	/**
	 * @return the tabbedPane
	 */
	private JTabbedPane getTabbedPane() {
		if (tabbedPane == null) {
			tabbedPane = new JTabbedPane();

			tabbedPane.addTab("Pending Games", null, getPendingListContentPane(),
					"The games in the list are currently running on the server. Choose one to watch.");
			tabbedPane.addTab("New Game", null, getNewGameContentPane(),
					"Enter the size of the board you want to play on in this form.");
			if (false) {
				tabbedPane.addTab("Connected Players", null, getPlayersListContentPane(),
						"These players are currently connected to the server. Choose one to propose a game.");
				tabbedPane.addTab("Started Games", null, getGamesListContentPane(),
						"The games in the list are currently running on the server. Choose one to watch.");
			}
		}
		return tabbedPane;
	}

	//	methods building the tabs
	
	private JPanel getPendingListContentPane() {
		if (pendingListContentPane == null) {
			pendingListContentPane = new JPanel();
			pendingListContentPane.setName("pendingListContentPane");
			pendingListContentPane.setLayout(
					new javax.swing.BoxLayout(pendingListContentPane,
							javax.swing.BoxLayout.Y_AXIS));

			pendingListContentPane.add(getPendingListLabel(), null);
			pendingListContentPane.add(getPendingList(), null);
			
			pendingListContentPane.add(getConnectButton());
		}
		return pendingListContentPane;
	}

	private JPanel getNewGameContentPane() {
		if (newGameContentPane == null) {
			newGameContentPane = new JPanel();
			newGameContentPane.setName("newGameContentPane");
			newGameContentPane.setLayout(
					new javax.swing.BoxLayout(newGameContentPane,
							javax.swing.BoxLayout.Y_AXIS));

			newGameContentPane.add(getNewGameLabel(), null);
			newGameContentPane.add(getBoardSizeLabel(), null);
			newGameContentPane.add(getBoardSizeSlider(), null);
			
			newGameContentPane.add(getStartButton());
		}
		return newGameContentPane;
	}
	
	private JPanel getPlayersListContentPane() {
		if (playersListContentPane == null) {
			playersListContentPane = new JPanel();
			playersListContentPane.setName("playersListContentPane");
			playersListContentPane.setLayout(
					new javax.swing.BoxLayout(playersListContentPane,
							javax.swing.BoxLayout.Y_AXIS));

			playersListContentPane.add(getPlayersListLabel(), null);
//			playersListContentPane.add(getPendingList(), null);
//			
//			playersListContentPane.add(getConnectButton());			
		}
		return playersListContentPane;
	}

	private JPanel getGamesListContentPane() {
		if (gamesListContentPane == null) {
			gamesListContentPane = new JPanel();
			gamesListContentPane.setName("gamesListContentPane");
			gamesListContentPane.setLayout(
					new javax.swing.BoxLayout(gamesListContentPane,
							javax.swing.BoxLayout.Y_AXIS));

			gamesListContentPane.add(getGamesListLabel(), null);
//			gamesListContentPane.add(getPendingList(), null);
//			
//			gamesListContentPane.add(getConnectButton());
		}
		return gamesListContentPane;
	}

	//	methods building the "pending list" view
	
	/**
	 * Return the pendingListLabel property value.
	 * @return javax.swing.JLabel
	 */
	private JLabel getPendingListLabel() {
		if (pendingListLabel == null) {
			pendingListLabel = new javax.swing.JLabel();
			pendingListLabel.setName("pendingListLabel");
			pendingListLabel.setText("Choose a Game");
			pendingListLabel.setToolTipText("The games in the list are currently " +
					"running on the server and waiting for a partner. " +
					"Choose one to join.");
		}
		return pendingListLabel;
	}

	private JScrollPane getPendingList() {
		if (pendingList == null) {
			model = new DefaultListModel();
			ArrayList<GameData> tempData = getGames();
			for (int i = 0; i < tempData.size(); i++) {
				model.addElement(tempData.get(i));
			}
			
			pendingList = new JList(model);
	        pendingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	        pendingList.setLayoutOrientation(JList.VERTICAL);
	        pendingList.setVisibleRowCount(-1);
	        
	        listScroller = new JScrollPane(pendingList);
	        listScroller.setAlignmentX(LEFT_ALIGNMENT);
	        
	        new UpdateThread (UPDATE_PERIOD).start();
	    }
		return listScroller;
	}
	
	/**
	 * Return the connectButton property value.
	 * @return javax.swing.JButton
	 */
	private JButton getConnectButton() {
		if (connectButton == null) {
			connectButton = new javax.swing.JButton();
			connectButton.setName("connectButton");
			connectButton.setText("Connect to Server");
			connectButton.addActionListener(
				new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent e) {
						if (e.getActionCommand().equals (connectButton.getText())) {
							int index = pendingList.getSelectedIndex();
							setGameList();
							if (index >= 0 && index < gameData.length) {
								connectionData.setGame(gameData[index].getName());
								connectionData.setBoardSize(gameData[index].getSize());
								
								stopped = true;
								setVisible (false);
							}
						}
					}
				});
		}
		return connectButton;
	}

	//	methods building the "new game" view

	/**
	 * @return Returns the newGameLabel.
	 */
	private JLabel getNewGameLabel() {
		if (newGameLabel == null) {
			newGameLabel = new javax.swing.JLabel();
			newGameLabel.setName("newGameLabel");
			newGameLabel.setText("Start a new Game");
			newGameLabel.setToolTipText("Enter the size of the board you want " +
					"to play on in this form.");
		}
		return newGameLabel;
	}

	/**
	 * Return the sizeLabel property value.
	 * @return JLabel
	 */
	private JLabel getBoardSizeLabel() {
		if (sizeLabel == null) {
			sizeLabel = new JLabel();
			sizeLabel.setName("sizeLabel");
			sizeLabel.setText("Board size: "+String.valueOf(this.boardSize)+"x"
					+String.valueOf(this.boardSize)+"x"+String.valueOf(this.boardSize));
			
			sizeLabel.setToolTipText("The size of the board on which you want" +
					" to play. This is not adjustable yet, you have to take" +
					" whichever size the server offers you.");
			sizeLabel.setEnabled (true);
		}
		return sizeLabel;
	}

	/**
	 * Return the sizeSlider property value.
	 * @return JSlider
	 */
	private JSlider getBoardSizeSlider() {
		if (sizeSlider == null) {
			sizeSlider = new JSlider();
			sizeSlider.setName("sizeSlider");
			sizeSlider.setMinimum(GameBase.MIN_GRID_SIZE);
			sizeSlider.setMaximum(GameBase.MAX_GRID_SIZE);
			sizeSlider.setMinorTickSpacing(2);
			sizeSlider.setValue(this.boardSize);
			sizeSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					String boardSizeString = String.valueOf(sizeSlider.getValue());
					sizeLabel.setText("Board Size: "+boardSizeString+"x"
							+boardSizeString+"x"+boardSizeString);
					connectionData.setBoardSize(sizeSlider.getValue());
				}
			});
			
			sizeSlider.setToolTipText("The size of the board on which you want" +
					" to play. This is not adjustable yet, you have to take" +
					" whichever size the server offers you.");
			sizeSlider.setEnabled(true);
		}
		return sizeSlider;
	}
	
	/**
	 * Return the connectButton property value.
	 * @return JButton
	 */
	private JButton getStartButton() {
		if (startButton == null) {
			startButton = new JButton();
			startButton.setName("startButton");
			startButton.setText("Start Game");
			startButton.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(java.awt.event.ActionEvent e) {
					if (e.getActionCommand().equals (startButton.getText())) {
						System.out.println("actionPerformed(): "+e.getActionCommand()); // TODO Auto-generated Event stub actionPerformed()
			    		connectionData.setStartGame(true);
						setVisible (false);
						
					}
				}
			});
		}
		return startButton;
	}
	
	//	methods building the "players list" view
	
	/**
	 * @return Returns the playersListLabel.
	 */
	private JLabel getPlayersListLabel() {
		if (playersListLabel == null) {
			playersListLabel = new javax.swing.JLabel();
			playersListLabel.setName("playersListLabel");
			playersListLabel.setText("Players currently connected");
			playersListLabel.setToolTipText("These players are currently "+
					"connected to the server. Choose one to propose a game.");
		}
		return playersListLabel;
	}

	//	methods building the "games list" view

	/**
	 * @return Returns the gamesListLabel.
	 */
	private JLabel getGamesListLabel() {
		if (gamesListLabel == null) {
			gamesListLabel = new javax.swing.JLabel();
			gamesListLabel.setName("gamesListLabel");
			gamesListLabel.setText("Choose a Game");
			gamesListLabel.setToolTipText("The games in the list are currently " +
					"running on the server. Choose one to watch.");
		}
		return gamesListLabel;
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
		this.setSize(480, 300);
		this.setTitle("Choose a Game");
		this.setContentPane(getTabbedPane());
	}
	
	
	////////////////////////////////////////////////////////////////////////////
	//                                                                        //
	//          VARIABLES SECTION STARTS                                      //
	//                                                                        //
	////////////////////////////////////////////////////////////////////////////	
		
	//	root level container
	private JTabbedPane tabbedPane = null;

	//	containers inside tabs
	private JPanel pendingListContentPane = null;
	private JPanel newGameContentPane = null;
	private JPanel playersListContentPane = null;
	private JPanel gamesListContentPane = null;
	
	//	pending list elements
	private JLabel pendingListLabel = null;
	DefaultListModel model = null;
	private JList pendingList = null;
	private JScrollPane listScroller;
	private JButton connectButton = null;
	
	//	new game elements
	private JLabel newGameLabel = null;
	private JCheckBox startGameCheckBox = null;
	private JLabel sizeLabel = null;
	private JSlider sizeSlider = null;
	private JButton startButton = null;
	
	//	players list elements
	private JLabel playersListLabel = null;
	
	//	games list elements
	private JLabel gamesListLabel = null;		

	//	state members
	private ConnectionData connectionData;
	private ConnectedPlayer player;

	private GameData[] gameData = {};

	private int boardSize = 3;

	/**	whether dialog has been stopped => game has begun */
	private boolean stopped = false;
	
	/**	milliseconds to wait between updates of the game list */
	static final int UPDATE_PERIOD = 1000;
	
	private static final long serialVersionUID = 7199599938553022819L;

//	public static void main(String[] args) {
//		new ChooseGameDialog(new ConnectionData()).setVisible(true);
//	}

}  //  @jve:decl-index=0:visual-constraint="10,10"
