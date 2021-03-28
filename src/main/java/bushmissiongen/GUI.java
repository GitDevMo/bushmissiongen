package bushmissiongen;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;

import bushmissiongen.messages.ErrorMessage;
import bushmissiongen.messages.Message;

public class GUI extends JFrame implements ActionListener {
	private static final long serialVersionUID = 1L;

	private BushMissionGen mMyApp;

	private JPanel mPanelTop;
	public JLabel mInputPathLabel;
	public JTextField mInputPathField;
	private JButton mGenerateButton, mSelectButton;
	private JMenuItem mExitItem, mConvertItem, mHelpDocItem, mHelpPdfItem, mProjectPageItem, mCompileItem, mEditItem, mShowPlanesItem;
	private JMenu mRecentFiles, mExtrasFiles;
	private List<JMenuItem> mRecentFilesList = new ArrayList<>();
	private Map<JMenuItem, File> mExtrasFilesMap = new HashMap<>();
	private JMenuItem mNavItem1, mNavItem2, mNavItem3, mNavItem4, mNavItem5, mNavItem6;
	private JScrollPane mScrollPane;
	public JTextArea mTextArea;
	private static String mLastFolder = "";

	public GUI(BushMissionGen app) {
		super("BushMissionGen v" + BushMissionGen.VERSION);
		mMyApp = app;

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		// Menus
		JMenuBar menubar = new JMenuBar();
		JMenu fileMenu = new JMenu ("File");
		mRecentFiles = new JMenu("Recent files");
		fileMenu.add (mRecentFiles);
		populateRecentFiles();
		mExitItem = new JMenuItem ("Exit");
		fileMenu.add (mExitItem);
		mExitItem.addActionListener(this);
		menubar.add (fileMenu);

		JMenu navigationMenu = new JMenu ("Navigation");
		mNavItem1 = new JMenuItem ("Open project folder");
		navigationMenu.add (mNavItem1);
		mNavItem1.addActionListener (this);
		mNavItem2 = new JMenuItem ("Open images folder");
		navigationMenu.add (mNavItem2);
		mNavItem2.addActionListener (this);
		mNavItem3 = new JMenuItem ("Open sound folder");
		navigationMenu.add (mNavItem3);
		mNavItem3.addActionListener (this);
		mNavItem4 = new JMenuItem ("Open compile folder");
		navigationMenu.add (mNavItem4);
		mNavItem4.addActionListener (this);
		mNavItem5 = new JMenuItem ("Open Community folder");
		navigationMenu.add (mNavItem5);
		mNavItem5.addActionListener (this);
		mNavItem6 = new JMenuItem ("Open Saves folder");
		navigationMenu.add (mNavItem6);
		mNavItem6.addActionListener (this);
		menubar.add (navigationMenu);

		JMenu toolsMenu = new JMenu ("Tools");
		mEditItem = new JMenuItem ("Edit with Notepad++");
		toolsMenu.add (mEditItem);
		mEditItem.addActionListener (this);
		mShowPlanesItem = new JMenuItem ("Show available planes...");
		toolsMenu.add (mShowPlanesItem);
		mShowPlanesItem.addActionListener (this);
		mConvertItem = new JMenuItem ("Convert PLN to input file...");
		toolsMenu.add (mConvertItem);
		mConvertItem.addActionListener (this);
		mCompileItem = new JMenuItem ("Compile bush mission");
		toolsMenu.add (mCompileItem);
		mCompileItem.addActionListener (this);
		menubar.add (toolsMenu);

		JMenu helpMenu = new JMenu ("Help");
		mProjectPageItem = new JMenuItem ("Go to the project home page");
		helpMenu.add (mProjectPageItem);
		mProjectPageItem.addActionListener (this);
		mHelpDocItem = new JMenuItem ("Show the manual (DOCX)");
		helpMenu.add (mHelpDocItem);
		mHelpDocItem.addActionListener (this);
		mHelpPdfItem = new JMenuItem ("Show the manual (PDF)");
		helpMenu.add (mHelpPdfItem);
		mHelpPdfItem.addActionListener (this);
		mExtrasFiles = new JMenu("Extras");
		helpMenu.add (mExtrasFiles);
		populateExtrasFiles();

		menubar.add (helpMenu);
		setJMenuBar (menubar);

		// TOP
		mPanelTop = new JPanel();
		mPanelTop.setLayout(new FlowLayout());

		mInputPathLabel = new JLabel("Input file:");

		mInputPathField = new JTextField(45);
		mInputPathField.setHorizontalAlignment(JTextField.LEFT);
		mInputPathField.setText("");

		mGenerateButton = new JButton("Generate");
		mSelectButton = new JButton("Select");

		mPanelTop.add(mInputPathLabel);
		mPanelTop.add(mInputPathField);
		mPanelTop.add(mSelectButton);
		mPanelTop.add(mGenerateButton);

		// TextArea + Scrollpane
		mTextArea = new JTextArea();
		mTextArea.setEditable(false);
		mTextArea.setBorder(new EmptyBorder(0, 5, 5, 5));
		mScrollPane = new JScrollPane(mTextArea);

		// ---------------

		this.getContentPane().setLayout(new BorderLayout());
		this.getContentPane().add(mPanelTop, BorderLayout.NORTH);
		this.getContentPane().add(mScrollPane, BorderLayout.CENTER);

		mGenerateButton.addActionListener(this);
		mSelectButton.addActionListener(this);

		this.setSize (800, 500);
		this.setLocationRelativeTo(null);

		if (mMyApp.mArgs != null && mMyApp.mArgs.length>0) {
			File argFile = new File(mMyApp.mArgs[0]);
			if (argFile.exists() && !argFile.isDirectory()) {
				mInputPathField.setText(argFile.getAbsolutePath());
			}
		}

		if (!mInputPathField.getText().isEmpty()) {
			showFileContents(mInputPathField.getText());
		} else {
			showFileContents("README.txt");
		}

		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent windowEvent) {
				mMyApp.mSettings.saveSettings();
				dispose();
			}
		});

		setVisible(true);
	}

	private void populateRecentFiles() {
		// Populate recent files
		mRecentFiles.removeAll();
		mRecentFilesList.clear();

		for (String file : mMyApp.mSettings.recentFiles) {
			JMenuItem item = new JMenuItem(file);
			item.addActionListener (this);
			mRecentFiles.add(item);
			mRecentFilesList.add(item);
		}
	}

	private void populateExtrasFiles() {
		// Populate recent files
		mExtrasFiles.removeAll();
		mExtrasFilesMap.clear();

		File extrasPath = new File(System.getProperty("user.dir") + File.separator + "extras");
		File[] extrasFiles = extrasPath.listFiles();

		for (File file : extrasFiles) {
			JMenuItem item = new JMenuItem(file.getName());
			item.addActionListener (this);
			mExtrasFiles.add(item);
			mExtrasFilesMap.put(item, file);
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource()==mGenerateButton) {
			// Nothing selected?
			if (mInputPathField.getText().trim().isEmpty()) {
				select();
			}

			generate();
		} else if (e.getSource()==mSelectButton) {
			select();
		} else if (e.getSource()==mExitItem) {
			WindowEvent wev = new WindowEvent(this, WindowEvent.WINDOW_CLOSING);
			Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(wev);
		} else if (e.getSource()==mShowPlanesItem) {
			String planesText = mMyApp.mSimData.getPlanes();
			mTextArea.setText(planesText);
			mTextArea.setCaretPosition(0);
		} else if (e.getSource()==mConvertItem) {
			convert();
		} else if (e.getSource()==mProjectPageItem) {
			showURL("https://flightsim.to/file/3681/bushmissiongen");
		} else if (e.getSource()==mHelpDocItem) {
			showURL("README.docx");
		} else if (e.getSource()==mHelpPdfItem) {
			showURL("README.pdf");
		} else if (e.getSource()==mCompileItem) {
			Message msg = mMyApp.generate(mInputPathField.getText(), 1);
			if (msg != null) {
				if (msg instanceof ErrorMessage) {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Compile", JOptionPane.ERROR_MESSAGE);
				} else {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Compile", JOptionPane.INFORMATION_MESSAGE);
				}
			} else {
				JOptionPane.showMessageDialog(mMyApp.mGUI, "Finished!", "Compile", JOptionPane.INFORMATION_MESSAGE);
			}
		} else if (e.getSource()==mEditItem) {
			Message msg = mMyApp.generate(mInputPathField.getText(), 2);
			if (msg != null) {
				if (msg instanceof ErrorMessage) {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Edit", JOptionPane.ERROR_MESSAGE);
				} else {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Edit", JOptionPane.INFORMATION_MESSAGE);
				}
			}			
		} else if (e.getSource()==mNavItem1) {
			Message msg = mMyApp.generate(mInputPathField.getText(), 3);
			if (msg != null) {
				if (msg instanceof ErrorMessage) {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Navigation", JOptionPane.ERROR_MESSAGE);
				} else {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Navigation", JOptionPane.INFORMATION_MESSAGE);
				}
			}			
		} else if (e.getSource()==mNavItem2) {
			Message msg = mMyApp.generate(mInputPathField.getText(), 4);
			if (msg != null) {
				if (msg instanceof ErrorMessage) {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Navigation", JOptionPane.ERROR_MESSAGE);
				} else {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Navigation", JOptionPane.INFORMATION_MESSAGE);
				}
			}			
		} else if (e.getSource()==mNavItem3) {
			Message msg = mMyApp.generate(mInputPathField.getText(), 5);
			if (msg != null) {
				if (msg instanceof ErrorMessage) {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Navigation", JOptionPane.ERROR_MESSAGE);
				} else {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Navigation", JOptionPane.INFORMATION_MESSAGE);
				}
			}
		} else if (e.getSource()==mNavItem4) {
			Message msg = mMyApp.generate(mInputPathField.getText(), 6);
			if (msg != null) {
				if (msg instanceof ErrorMessage) {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Navigation", JOptionPane.ERROR_MESSAGE);
				} else {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Navigation", JOptionPane.INFORMATION_MESSAGE);
				}
			}			
		} else if (e.getSource()==mNavItem5 || e.getSource()==mNavItem6) {
			if (BushMissionGen.COMMUNITY_DIR != null) {
				String useCommunityDir = BushMissionGen.COMMUNITY_DIR;
				File useCommunityPath = new File(BushMissionGen.COMMUNITY_DIR);

				if (e.getSource()==mNavItem5) {
					Message msg = mMyApp.mFileHandling.showFolder(useCommunityDir);
					if (msg != null) {
						if (msg instanceof ErrorMessage) {
							JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Navigation", JOptionPane.ERROR_MESSAGE);
						} else {
							JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Navigation", JOptionPane.INFORMATION_MESSAGE);
						}
					}
					return;
				}

				String saveFolder = useCommunityPath.getParentFile().getParentFile().getParentFile().getAbsolutePath() + File.separator + "LocalState\\MISSIONS\\ACTIVITIES";
				File saveFolderPath = new File(saveFolder);
				if (saveFolderPath.exists()) {
					Message msg = mMyApp.mFileHandling.showFolder(saveFolder);
					if (msg != null) {
						if (msg instanceof ErrorMessage) {
							JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Navigation", JOptionPane.ERROR_MESSAGE);
						} else {
							JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Navigation", JOptionPane.INFORMATION_MESSAGE);
						}
					}
					return;

				} else {
					JOptionPane.showMessageDialog(mMyApp.mGUI, "Cannot find the Save folder.", "Navigation", JOptionPane.ERROR_MESSAGE);
				}
			} else {
				JOptionPane.showMessageDialog(mMyApp.mGUI, "Cannot find the Community folder.", "Navigation", JOptionPane.ERROR_MESSAGE);
			}
		} else {
			// Recent files?
			for (JMenuItem jmi : mRecentFilesList) {
				if (e.getSource()==jmi) {
					String recentString = jmi.getText();
					File recentFile = new File(recentString);
					selectShow(recentFile);
					break;
				}
			}

			// Extras files?
			for (JMenuItem jmi : mExtrasFilesMap.keySet()) {
				if (e.getSource()==jmi) {
					File extrasFile = mExtrasFilesMap.get(jmi);
					showURL(extrasFile.toURI().toString());
					break;
				}
			}
		}
	}

	private void select() {
		final JFileChooser fc = new JFileChooser();
		if (mLastFolder.isEmpty()) {
			fc.setCurrentDirectory(new File("."));
		} else {
			File curDir = new File(mLastFolder);
			if (curDir.exists()) {
				fc.setCurrentDirectory(curDir);
			} else {
				fc.setCurrentDirectory(new File("."));
			}
		}
		fc.setAcceptAllFileFilterUsed(false);
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fc.setFileFilter(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.isDirectory() || (!f.getName().equalsIgnoreCase("README.txt") && (f.getName().toLowerCase().endsWith(".txt") || f.getName().toLowerCase().endsWith(".xlsx")));
			}

			@Override
			public String getDescription() {
				return "Input files only";
			}
		});

		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = fc.getSelectedFile();
			mLastFolder = file.getParent();
			selectShow(file);
		}		
	}

	private void selectShow(File file) {
		mMyApp.mSettings.addRecent(file.getAbsolutePath());
		populateRecentFiles();

		mInputPathField.setText(file.getAbsolutePath());
		mMyApp.mPOIs = null;
		mMyApp.mSounds = null;

		if (!file.getName().toLowerCase().endsWith(".xlsx")) {
			showFileContents(file.getAbsolutePath());
		} else {
			String contents = String.join(System.lineSeparator(), mMyApp.mFileHandling.readFromXLS(file.getAbsolutePath()));
			mTextArea.setText(contents);
			mTextArea.setCaretPosition(0);
		}
	}

	private void generate() {
		Message msg = mMyApp.generate(mInputPathField.getText(), 0);
		if (msg != null) {
			if (msg instanceof ErrorMessage) {
				JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Generate", JOptionPane.ERROR_MESSAGE);
			} else {
				JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Generate", JOptionPane.INFORMATION_MESSAGE);
			}
		} else {
			if (mMyApp.mSavedPreviewFile != null) {
				// Preview?
				Object[] options = {"OK",
				"HTML preview"};
				int nPRV = JOptionPane.showOptionDialog(mMyApp.mGUI,
						"Finished!",
						"Generate",
						JOptionPane.YES_NO_CANCEL_OPTION,
						JOptionPane.INFORMATION_MESSAGE,
						null,
						options,
						options[0]);
				if (nPRV == 1) {
					File file = new File(mMyApp.mSavedPreviewFile);
					showURL(file.toURI().toString());
				}
			} else {
				JOptionPane.showMessageDialog(mMyApp.mGUI, "Finished!", "Generate", JOptionPane.INFORMATION_MESSAGE);
			}
		}		
	}

	private void convert() {
		final JFileChooser fc = new JFileChooser();
		if (mLastFolder.isEmpty()) {
			fc.setCurrentDirectory(new File("."));
		} else {
			File curDir = new File(mLastFolder);
			if (curDir.exists()) {
				fc.setCurrentDirectory(curDir);
			} else {
				fc.setCurrentDirectory(new File("."));
			}
		}
		fc.setAcceptAllFileFilterUsed(false);
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fc.setFileFilter(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.isDirectory() || (!f.getName().contains("template") && f.getName().toLowerCase().endsWith(".pln"));
			}

			@Override
			public String getDescription() {
				return "PLN files only";
			}
		});

		if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = fc.getSelectedFile();
			mLastFolder = file.getParent();
			Message msg = mMyApp.convertPLN(file);
			if (msg != null) {
				if (msg instanceof ErrorMessage) {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Convert", JOptionPane.ERROR_MESSAGE);
				} else {
					JOptionPane.showMessageDialog(mMyApp.mGUI, msg.getMessage(), "Convert", JOptionPane.INFORMATION_MESSAGE);
				}
			}
		}		
	}

	public void showURL(String url) {
		if(Desktop.isDesktopSupported()) {
			Desktop desktop = Desktop.getDesktop();
			try {
				desktop.browse(new URI(url));
			} catch (IOException | URISyntaxException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else{
			Runtime runtime = Runtime.getRuntime();
			try {
				runtime.exec("xdg-open " + url);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public String showFileContents(String file) {
		try {
			Path path = Paths.get(file);
			String contents = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			mTextArea.setText(contents);
			mTextArea.setCaretPosition(0);
		} catch (IOException e1) {
			return "Could not read file: " + file;
		}
		return null;
	}
}
