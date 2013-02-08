package matchmaker;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.filechooser.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;

public class UploadApplet extends JApplet implements ActionListener, UploadListener {
	static final long serialVersionUID = 1L;
	static double version = 1.1;
	
	String SERVLET_URL;
	
	final String[] ROLES = {"db1", "db2", "commands"};
	final int NUM_STEPS = 5;
	final int APPLET_WIDTH = 500;
	final int APPLET_HEIGHT = 525;
	
	//For 'login' interface:
	JLabel instructionsLabel;
	JLabel responseLabel;
	JTextField idField;
	JButton checkIdButton;
	JButton newIdButton;
	
	//For main interface:
	File[] files = new File[3];
	JFileChooser[] jfcs = new JFileChooser[3];
	JLabel[] upStatusLabels = new JLabel[3];
	JLabel[] roleLabels = new JLabel[3];
	JLabel[] fNameLabels = new JLabel[3];
	JButton[] chooseButtons = new JButton[3];
	JButton[] uploadButtons = new JButton[3];
	JLabel[] stepLabels = new JLabel[NUM_STEPS];
	JButton[] commandButtons = new JButton[NUM_STEPS];
	JButton[] dlButtons = new JButton[NUM_STEPS];
	JLabel[] stepStatusLabels = new JLabel[NUM_STEPS];
	
	MatchmakerServletConnector sConnector;
	
	public void init() {

		//Get IP - only necessary while on laptop b/c of non-static IP
		SERVLET_URL = this.getParameter("servletURL");
		sConnector = new MatchmakerServletConnector(SERVLET_URL, -1);
		
		//Set up first page of UI
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				public void run() {
					setUpFirstUI();					
				}
			});
		} catch(Exception e) {e.printStackTrace();}
		
	}

	//Implements ActionListener.actionPerformed
	public void actionPerformed(ActionEvent event) {
		
		JButton src = (JButton)(event.getSource());
		System.out.println(src.getText());

		//Login screen: Check ID
		if (src == checkIdButton) {
			try {
				sConnector.setID(Integer.parseInt(idField.getText()));
			} catch (NumberFormatException e) {
				instructionsLabel.setText("IDs are numbers. Please press 'Generate New ID' to get an ID number.");
			}
			try {
				UserStatus s = sConnector.getStatus();
				if (s.stepsDone > -2)
					setUpMainUI(s);
				else
					instructionsLabel.setText("No records found for that ID. Please enter a valid ID or press 'Generate New ID'.");
			} catch(IOException e) {
				e.printStackTrace();
				idField.setText("Error connecting to server.");
			}
			return;
		}
		
		//Login screen: Generate new ID
		if (src == newIdButton) {
			try {
				idField.setText("" + sConnector.generateNewID());
			} catch(IOException e) {
				e.printStackTrace();
				idField.setText("Error connecting to server.");
			}
		}
		
		//"Browse..." buttons
		for (int ii = 0; ii < 3; ii++) {
			if (src == chooseButtons[ii]) {
				final int ff = ii;
				final UploadApplet parent = this;
				AccessController.doPrivileged(new PrivilegedAction<Object>() {
					public Object run() {
						int sodVal = jfcs[ff].showOpenDialog(parent);
						if (sodVal == JFileChooser.APPROVE_OPTION) {
							files[ff] = jfcs[ff].getSelectedFile();
							long size = files[ff].length();
							String sizeString = "" + (size / 1000) + "KB";
							fNameLabels[ff].setText(files[ff].getName() + " (" + sizeString + ")");
							upStatusLabels[ff].setText("Ready");
							uploadButtons[ff].setEnabled(true);
						}
						return null;
					}
				});
			return;
			}
		}	

		//"Upload" buttons
		for (int ii = 0; ii < 3; ii++) {
			if (src == uploadButtons[ii]) {
				upStatusLabels[ii].setText("Uploading...");
				sConnector.upload(files[ii], ROLES[ii], this);
				return;
			}
		}

		//"Process" buttons
		for (int ii = 0; ii < NUM_STEPS; ii++) {
			if (src == commandButtons[ii]) {
				//Update step status label
				stepStatusLabels[ii].setText("Processing...");
				getContentPane().validate();
				getContentPane().paint(getGraphics());

				//Process instruction
				final int commandNum = ii + 1;
				(new SwingWorker<Object,Object>() {
					boolean success = true;
					
					//Send command to server
					public Object doInBackground() {
						try {
							sConnector.doCommand(commandNum);
						} catch (IOException e) {
							e.printStackTrace();
							success = false;
						}						
						return null;
					}

					//Update GUI
					protected void done() {
						if (success) {
							stepStatusLabels[commandNum - 1].setText("Done");
							dlButtons[commandNum - 1].setEnabled(true);
							if (commandNum < commandButtons.length)
								commandButtons[commandNum].setEnabled(true);
						}else
							stepStatusLabels[commandNum - 1].setText("Connection error");
					}
				}).execute();
				
				return;
			}
		}
		
		//"Get Results" buttons
		for (int ii = 0; ii < dlButtons.length; ii++) {
			if (src == dlButtons[ii]) {
				try {
					final URL resultURL = sConnector.getResults(ii + 1);
					final UploadApplet alias = this;
					AccessController.doPrivileged(new PrivilegedAction<Object>() {
						public Object run() {
							alias.getAppletContext().showDocument(resultURL, "_blank");
							return null;
						}
					});
				}
				catch (MalformedURLException e){
					e.printStackTrace();
					stepStatusLabels[ii].setText("Fetch error");
				}
				catch (IOException e){
					e.printStackTrace();
					stepStatusLabels[ii].setText("Connection error");
				}
				return;
			}
		}
	}

	//Implements matchmaker.UploadListener.uploadEventPerformed
	//An UploadThread finished - upload complete or error passed
	public void uploadEventPerformed(UploadEvent event) {
		UploadThread source = (UploadThread) event.getSource();
		System.out.println(source.role);
		
		if (event.getMessage() == UploadEvent.UPLOAD_FINISHED) {
			for (int ii = 0, jj = ROLES.length; ii < jj; ii++) {
				if (source.role.equals(ROLES[ii]))
					upStatusLabels[ii].setText("Done");
			}
			//If all 3 files are now uploaded, allow first step
			try {
				if (sConnector.getStatus().stepsDone == 0)
					commandButtons[0].setEnabled(true);
			} catch(IOException e) {
				stepStatusLabels[0].setText("Error connecting to server.");
			}
		}
			
	}

	//Gets first page of UI ready - this is the page that asks for user id
	public void setUpFirstUI() {
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			this.setSize(APPLET_WIDTH, APPLET_HEIGHT);
			this.setLayout(new FlowLayout());
			
			instructionsLabel = new JLabel();
			instructionsLabel.setText("<html>If you have a user ID for your project, please enter it and press 'Log in.'<br><br>" +
					"If this is your first visit, or you are processing a new pair of databases,<br>" +
					"please press 'Generate New ID', then 'Log in.'<br><br>" +
					"Remember your ID number and use it each time you return to this project.");
			idField = new JTextField();
			idField.setColumns(20);
//			idField.setText(""+version);
			checkIdButton = new JButton("Log In");
				checkIdButton.addActionListener(this);
			newIdButton = new JButton("Generate New ID");
				newIdButton.addActionListener(this);
			add(instructionsLabel);
			add(idField);
			add(checkIdButton);
			add(newIdButton);
			getContentPane().validate();
			getContentPane().setVisible(true);
			repaint();
		} catch(Exception e) {e.printStackTrace();}
		
	}

	//Gets second page of UI ready - main application controls
	public void setUpMainUI(UserStatus status) {
		try {
			//Set up containers
			Container pane = getContentPane();
			JPanel instructionsPanel = new JPanel();
			JPanel upPan = new JPanel();
			JPanel comPan = new JPanel();
			
			//Clear everything
			pane.removeAll();
			
			//Get container layouts ready
			GroupLayout layout = new GroupLayout(pane);
			GroupLayout upLay = new GroupLayout(upPan);
			GroupLayout comLay = new GroupLayout(comPan);
			pane.setLayout(layout);
			upPan.setLayout(upLay);
			comPan.setLayout(comLay);
			layout.setAutoCreateGaps(true);
			layout.setAutoCreateContainerGaps(true);
			upLay.setAutoCreateGaps(true);
			upLay.setAutoCreateContainerGaps(true);
			comLay.setAutoCreateGaps(true);
			comLay.setAutoCreateContainerGaps(true);

			//We'll be doing this in chunks to allow loops to make it easier
			GroupLayout.ParallelGroup[] uploadTallGroups = new GroupLayout.ParallelGroup[5];
			GroupLayout.ParallelGroup[] uploadWideGroups = new GroupLayout.ParallelGroup[3];
			GroupLayout.ParallelGroup[] commandTallGroups = new GroupLayout.ParallelGroup[4];
			GroupLayout.ParallelGroup[] commandWideGroups = new GroupLayout.ParallelGroup[NUM_STEPS];
			for (int ii = 0; ii < 5; ii++)
				uploadTallGroups[ii] = upLay.createParallelGroup(GroupLayout.Alignment.LEADING);
			for (int ii = 0; ii < 4; ii++)
				commandTallGroups[ii] = comLay.createParallelGroup(GroupLayout.Alignment.LEADING);
			
			//Set up components
			//Upload components:
			for (int ii = 0; ii < 3; ii++) {
				chooseButtons[ii] = new JButton("Browse...");
				chooseButtons[ii].addActionListener(this);
				uploadButtons[ii] = new JButton("Upload");
				uploadButtons[ii].setEnabled(false);
				uploadButtons[ii].addActionListener(this);

				//Apparently even initializing a JFileChooser requires permissions...
				jfcs[ii] = null;
				final int ff = ii;
				AccessController.doPrivileged(new PrivilegedAction<Object>() {
					public Object run() {
						jfcs[ff] = new JFileChooser();
						return null;
					}
				});

				roleLabels[ii] = new JLabel();
				fNameLabels[ii] = new JLabel("");
				upStatusLabels[ii] = new JLabel("Ready");
				
				uploadTallGroups[0].addComponent(roleLabels[ii]);
				uploadTallGroups[1].addComponent(fNameLabels[ii]);
				uploadTallGroups[2].addComponent(chooseButtons[ii]);
				uploadTallGroups[3].addComponent(uploadButtons[ii]);
				uploadTallGroups[4].addComponent(upStatusLabels[ii]);
				
				uploadWideGroups[ii] = upLay.createParallelGroup(GroupLayout.Alignment.BASELINE)
									.addComponent(roleLabels[ii])
									.addComponent(fNameLabels[ii])
									.addComponent(chooseButtons[ii])
									.addComponent(uploadButtons[ii])
									.addComponent(upStatusLabels[ii]);
			}
			//Finish JFileChooser setup:
			FileNameExtensionFilter csvFilter = new FileNameExtensionFilter("Comma-Separated Value Files (.csv, .txt)", "csv", "txt");
			jfcs[0].setFileFilter(csvFilter);
			jfcs[1].setFileFilter(csvFilter);
			jfcs[0].setAcceptAllFileFilterUsed(false);
			jfcs[1].setAcceptAllFileFilterUsed(false);
			FileNameExtensionFilter xmlFilter = new FileNameExtensionFilter("XML files (.xml, .txt)", "xml", "txt");
			jfcs[2].setFileFilter(xmlFilter);
			jfcs[2].setAcceptAllFileFilterUsed(false);
			
			//Command components:
			for (int ii = 0; ii < NUM_STEPS; ii++) {
				dlButtons[ii] = new JButton("Get Results");
				if (status.stepsDone <= ii)
					dlButtons[ii].setEnabled(false);
				dlButtons[ii].addActionListener(this);
				commandButtons[ii] = new JButton("Process");
//				if (status.stepsDone < ii)
//					commandButtons[ii].setEnabled(false);
				commandButtons[ii].addActionListener(this);
				
				stepLabels[ii] = new JLabel("Step " + (ii + 1) + ":");
				stepStatusLabels[ii] = new JLabel();
				if (status.stepsDone < ii)
					stepStatusLabels[ii].setText("Previous steps not done.");
				else if (status.stepsDone == ii)
					stepStatusLabels[ii].setText("Ready");
				else
					stepStatusLabels[ii].setText("Completed");

				commandTallGroups[0].addComponent(stepLabels[ii]);
				commandTallGroups[1].addComponent(commandButtons[ii]);
				commandTallGroups[2].addComponent(dlButtons[ii]);
				commandTallGroups[3].addComponent(stepStatusLabels[ii]);
				
				commandWideGroups[ii] = comLay.createParallelGroup(GroupLayout.Alignment.BASELINE)
									.addComponent(stepLabels[ii])
									.addComponent(commandButtons[ii])
									.addComponent(dlButtons[ii])
									.addComponent(stepStatusLabels[ii]);				
			}
			
			//Set up text, not in loops:
			fNameLabels[0].setText(status.db1);
			fNameLabels[1].setText(status.db2);
			fNameLabels[2].setText(status.commands);
			roleLabels[0].setText("First database: ");
			roleLabels[1].setText("Second database: ");
			roleLabels[2].setText("Command file: ");

			for (int ii = 0; ii < 3; ii++)
				if (!fNameLabels[ii].getText().equals(""))
					upStatusLabels[ii].setText("Previously uploaded");
			instructionsLabel.setText(
					"<html>1) Make sure that your database files are in .csv format.<br>" +
					"This can be achieved by exporting from Excel or any other statistics software.<br><br>" +
					"2) Create a command file for MatchmakerWeb according to the format described at ____.<br><br>" +
					"3) Upload your files. These will be stored on our server until you choose to delete them.<br><br>" +
					"4) Process your files by clicking the buttons below for each step in the Matchmaker process.<br><br>" +
					"5) Download your results after any step by clicking 'Get Results' for that step.<br>" +
					"You will likely only need to do this after the final step.");
			
			//Add components to JPanels:
			//Instructions:
			instructionsPanel.add(instructionsLabel);
			
			//Upload:
			GroupLayout.SequentialGroup uploadBigHoriz = upLay.createSequentialGroup();
			GroupLayout.SequentialGroup uploadBigVert = upLay.createSequentialGroup();
			for (int ii = 0; ii < 5; ii++)
				uploadBigHoriz.addGroup(uploadTallGroups[ii]);
			for (int ii = 0; ii < 3; ii++)
				uploadBigVert.addGroup(uploadWideGroups[ii]);
			upLay.setHorizontalGroup(uploadBigHoriz);
			upLay.setVerticalGroup(uploadBigVert);
			
			//Commands:
			GroupLayout.SequentialGroup commandBigHoriz = comLay.createSequentialGroup();
			GroupLayout.SequentialGroup commandBigVert = comLay.createSequentialGroup();
			for (int ii = 0; ii < 4; ii++)
				commandBigHoriz.addGroup(commandTallGroups[ii]);
			for (int ii = 0; ii < NUM_STEPS; ii++)
				commandBigVert.addGroup(commandWideGroups[ii]);
			comLay.setHorizontalGroup(commandBigHoriz);
			comLay.setVerticalGroup(commandBigVert);

			//Overall:
			layout.setHorizontalGroup(layout.createParallelGroup()
				.addComponent(instructionsPanel)
				.addComponent(upPan)
				.addComponent(comPan)
			);
			layout.setVerticalGroup(layout.createSequentialGroup()
					.addComponent(instructionsPanel)
					.addComponent(upPan)
					.addComponent(comPan)
			);
			
			//Set up borders
			instructionsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Instructions"));
			upPan.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Upload Files"));
			comPan.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), "Process Files"));
			
			//Be sure to show it
			pane.setSize(layout.minimumLayoutSize(pane));
			getContentPane().validate();
			repaint();

		} catch(Exception e) {e.printStackTrace();}
	}
	
	public void update(Graphics g) {
		paint(g);
	}
	
	public void paint(Graphics g) {
		super.paint(g);
	}
	
}