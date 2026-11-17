package com.nocturne.launcher;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;

/** Standalone Nocturne launcher: accounts, versions, RAM, one-click play. */
public class LauncherApp extends JFrame {
	private final ProfileStore.Data data = ProfileStore.load();
	private final CardLayout cards = new CardLayout();
	private final JPanel cardPanel = new JPanel(cards);
	private final JTextArea logArea = new JTextArea();
	private final JProgressBar progress = new JProgressBar();
	private final JComboBox<String> accountBox = new JComboBox<>();
	private final JComboBox<String> versionBox = new JComboBox<>();
	private final JTextField loaderField = new JTextField(10);
	private final JSlider ramSlider = new JSlider(1024, 16384, 4096);
	private final JLabel ramLabel = new JLabel();
	private final DefaultListModel<String> accountListModel = new DefaultListModel<>();
	private final JList<String> accountList = new JList<>(accountListModel);
	private final JTextField gameDirField = new JTextField(30);
	private final JTextField azureField = new JTextField(30);
	private volatile boolean launching;

	public static void main(String[] args) {
		FlatDarkLaf.setup();
		try {
			UIManager.put("Component.arc", 8);
			UIManager.put("Button.arc", 10);
		} catch (Exception ignored) {
		}
		SwingUtilities.invokeLater(LauncherApp::new);
	}

	public LauncherApp() {
		super("Nocturne Launcher");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setSize(920, 600);
		setMinimumSize(new Dimension(760, 480));
		setLocationRelativeTo(null);

		JPanel sidebar = new JPanel(new GridBagLayout());
		sidebar.setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		gbc.insets = new Insets(4, 0, 4, 0);
		JLabel brand = new JLabel("NOCTURNE");
		brand.setFont(brand.getFont().deriveFont(java.awt.Font.BOLD, 20f));
		gbc.gridy = 0;
		sidebar.add(brand, gbc);
		JLabel sub = new JLabel("minecraft launcher");
		sub.setFont(sub.getFont().deriveFont(11f));
		gbc.gridy = 1;
		sidebar.add(sub, gbc);
		String[] tabs = {"Play", "Accounts", "Settings"};
		gbc.gridy = 2;
		for (String tab : tabs) {
			JButton button = new JButton(tab);
			button.addActionListener(e -> cards.show(cardPanel, tab));
			gbc.gridy++;
			sidebar.add(button, gbc);
		}
		gbc.weighty = 1;
		gbc.gridy++;
		sidebar.add(new JLabel(""), gbc);

		cardPanel.add(buildPlayCard(), "Play");
		cardPanel.add(buildAccountsCard(), "Accounts");
		cardPanel.add(buildSettingsCard(), "Settings");

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(sidebar, BorderLayout.WEST);
		getContentPane().add(cardPanel, BorderLayout.CENTER);
		refreshAccounts();
		loadVersionsAsync();
		setVisible(true);
	}

	// ---------------- play ----------------

	private JPanel buildPlayCard() {
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		JPanel top = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.gridx = 0;
		gbc.gridy = 0;
		top.add(new JLabel("Account:"), gbc);
		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		accountBox.addActionListener(e -> {
			Object selected = accountBox.getSelectedItem();
			if (selected != null) {
				data.selectedAccount = selected.toString();
				ProfileStore.save(data);
			}
		});
		top.add(accountBox, gbc);
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		top.add(new JLabel("Version:"), gbc);
		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		versionBox.addActionListener(e -> {
			Object selected = versionBox.getSelectedItem();
			if (selected != null) {
				data.mcVersion = selected.toString();
				ProfileStore.save(data);
			}
		});
		top.add(versionBox, gbc);
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		top.add(new JLabel("Loader:"), gbc);
		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		loaderField.setToolTipText("Fabric loader version (blank = latest stable)");
		if (data.loaderVersion != null) {
			loaderField.setText(data.loaderVersion);
		}
		loaderField.addActionListener(e -> {
			data.loaderVersion = loaderField.getText().trim();
			ProfileStore.save(data);
		});
		top.add(loaderField, gbc);
		gbc.gridx = 0;
		gbc.gridy = 3;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		top.add(new JLabel("RAM:"), gbc);
		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		ramSlider.setMajorTickSpacing(4096);
		ramSlider.setPaintTicks(true);
		ramSlider.setValue(Math.max(1024, data.ramMb));
		updateRamLabel();
		ramSlider.addChangeListener(e -> {
			data.ramMb = ramSlider.getValue();
			updateRamLabel();
			ProfileStore.save(data);
		});
		JPanel ramRow = new JPanel(new BorderLayout(8, 0));
		ramRow.add(ramSlider, BorderLayout.CENTER);
		ramRow.add(ramLabel, BorderLayout.EAST);
		top.add(ramRow, gbc);
		panel.add(top, BorderLayout.NORTH);

		logArea.setEditable(false);
		logArea.setLineWrap(true);
		panel.add(new JScrollPane(logArea), BorderLayout.CENTER);

		JPanel bottom = new JPanel(new BorderLayout(8, 8));
		JButton playButton = new JButton("PLAY");
		playButton.setFont(playButton.getFont().deriveFont(java.awt.Font.BOLD, 16f));
		playButton.addActionListener(e -> onPlay());
		bottom.add(playButton, BorderLayout.CENTER);
		progress.setStringPainted(true);
		progress.setString("idle");
		bottom.add(progress, BorderLayout.SOUTH);
		panel.add(bottom, BorderLayout.SOUTH);
		return panel;
	}

	private void updateRamLabel() {
		ramLabel.setText((data.ramMb / 1024) + " GB");
	}

	private void log(String text) {
		SwingUtilities.invokeLater(() -> {
			logArea.append(text + "\n");
			logArea.setCaretPosition(logArea.getDocument().getLength());
		});
	}

	private void onPlay() {
		if (launching) {
			return;
		}
		ProfileStore.Account account = ProfileStore.find(data, data.selectedAccount);
		if (account == null && !data.accounts.isEmpty()) {
			account = data.accounts.get(0);
			data.selectedAccount = account.name;
		}
		if (account == null) {
			JOptionPane.showMessageDialog(this, "Add an account first (Accounts tab).", "No account", JOptionPane.WARNING_MESSAGE);
			return;
		}
		final ProfileStore.Account launchAccount = account;
		launching = true;
		progress.setIndeterminate(true);
		progress.setString("launching...");
		log("=== Nocturne launch ===");
		Thread thread = new Thread(() -> {
			try {
				GameLauncher.launch(data, launchAccount, this::log);
				log("Game process started. You can close the launcher or keep it open.");
				SwingUtilities.invokeLater(() -> {
					progress.setIndeterminate(false);
					progress.setString("running");
				});
			} catch (Exception e) {
				log("Launch failed: " + e.getMessage());
				SwingUtilities.invokeLater(() -> {
					progress.setIndeterminate(false);
					progress.setString("failed");
					JOptionPane.showMessageDialog(this, "Launch failed:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				});
			} finally {
				launching = false;
			}
		}, "nocturne-launch");
		thread.setDaemon(true);
		thread.start();
	}

	private void loadVersionsAsync() {
		Thread thread = new Thread(() -> {
			try {
				List<MojangApi.Entry> entries = MojangApi.releases();
				SwingUtilities.invokeLater(() -> {
					versionBox.removeAllItems();
					String wanted = data.mcVersion != null ? data.mcVersion : "26.3";
					for (MojangApi.Entry entry : entries) {
						versionBox.addItem(entry.id);
					}
					boolean found = false;
					for (int i = 0; i < versionBox.getItemCount(); i++) {
						if (wanted.equals(versionBox.getItemAt(i))) {
							versionBox.setSelectedIndex(i);
							found = true;
							break;
						}
					}
					if (!found && versionBox.getItemCount() > 0) {
						versionBox.setSelectedIndex(0);
						data.mcVersion = versionBox.getItemAt(0);
						ProfileStore.save(data);
					}
				});
			} catch (Exception e) {
				SwingUtilities.invokeLater(() -> {
					if (versionBox.getItemCount() == 0) {
						versionBox.addItem(data.mcVersion != null ? data.mcVersion : "26.3");
					}
					log("Version list unavailable (offline?): " + e.getMessage());
				});
			}
		}, "nocturne-versions");
		thread.setDaemon(true);
		thread.start();
	}

	// ---------------- accounts ----------------

	private JPanel buildAccountsCard() {
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		panel.add(new JScrollPane(accountList), BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton msButton = new JButton("Add Microsoft");
		msButton.addActionListener(e -> addMicrosoft());
		JButton elyButton = new JButton("Add Ely.by");
		elyButton.addActionListener(e -> addElyby());
		JButton offButton = new JButton("Add Offline");
		offButton.addActionListener(e -> addOffline());
		JButton useButton = new JButton("Set Active");
		useButton.addActionListener(e -> {
			String selected = accountList.getSelectedValue();
			if (selected != null) {
				data.selectedAccount = selected;
				ProfileStore.save(data);
				refreshAccounts();
			}
		});
		JButton removeButton = new JButton("Remove");
		removeButton.addActionListener(e -> {
			String selected = accountList.getSelectedValue();
			if (selected != null) {
				data.accounts.removeIf(a -> selected.equals(a.name));
				if (selected.equals(data.selectedAccount)) {
					data.selectedAccount = data.accounts.isEmpty() ? "" : data.accounts.get(0).name;
				}
				ProfileStore.save(data);
				refreshAccounts();
			}
		});
		buttons.add(msButton);
		buttons.add(elyButton);
		buttons.add(offButton);
		buttons.add(useButton);
		buttons.add(removeButton);
		panel.add(buttons, BorderLayout.SOUTH);
		return panel;
	}

	private void refreshAccounts() {
		accountListModel.clear();
		accountBox.removeAllItems();
		for (ProfileStore.Account account : data.accounts) {
			String label = account.name + " [" + account.type + "]";
			accountListModel.addElement(label);
			accountBox.addItem(account.name);
		}
		if (data.selectedAccount != null && !data.selectedAccount.isEmpty()) {
			accountBox.setSelectedItem(data.selectedAccount);
			for (int i = 0; i < accountListModel.size(); i++) {
				if (accountListModel.get(i).startsWith(data.selectedAccount + " ")) {
					accountList.setSelectedIndex(i);
					break;
				}
			}
		}
	}

	private void addOffline() {
		String name = JOptionPane.showInputDialog(this, "Player name (1-16 chars, offline play only):", "Add offline account", JOptionPane.PLAIN_MESSAGE);
		if (name == null) {
			return;
		}
		try {
			ElybyAuth.Session session = ElybyAuth.offline(name);
			ProfileStore.Account account = new ProfileStore.Account();
			account.type = "offline";
			account.name = session.name;
			account.uuid = session.uuid;
			account.accessToken = "0";
			upsert(account);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Invalid name", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void addElyby() {
		JTextField userField = new JTextField(20);
		JPasswordField passField = new JPasswordField(20);
		JPanel form = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.gridx = 0;
		gbc.gridy = 0;
		form.add(new JLabel("Ely.by email:"), gbc);
		gbc.gridx = 1;
		form.add(userField, gbc);
		gbc.gridx = 0;
		gbc.gridy = 1;
		form.add(new JLabel("Password:"), gbc);
		gbc.gridx = 1;
		form.add(passField, gbc);
		int result = JOptionPane.showConfirmDialog(this, form, "Add Ely.by account", JOptionPane.OK_CANCEL_OPTION);
		if (result != JOptionPane.OK_OPTION) {
			return;
		}
		Thread thread = new Thread(() -> {
			try {
				ElybyAuth.Session session = ElybyAuth.login(userField.getText().trim(), new String(passField.getPassword()));
				ProfileStore.Account account = new ProfileStore.Account();
				account.type = "elyby";
				account.name = session.name;
				account.uuid = session.uuid;
				account.accessToken = session.accessToken;
				account.clientToken = session.clientToken;
				SwingUtilities.invokeLater(() -> upsert(account));
				log("Ely.by login OK: " + session.name);
			} catch (Exception e) {
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Ely.by login failed:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
			}
		}, "nocturne-elyby");
		thread.setDaemon(true);
		thread.start();
	}

	private void addMicrosoft() {
		Thread thread = new Thread(() -> {
			try {
				MicrosoftAuth.DeviceCode code = MicrosoftAuth.startDeviceFlow(data.azureClientId);
				SwingUtilities.invokeLater(() -> showDeviceDialog(code));
			} catch (Exception e) {
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Microsoft login failed to start:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
			}
		}, "nocturne-ms-start");
		thread.setDaemon(true);
		thread.start();
	}

	private void showDeviceDialog(MicrosoftAuth.DeviceCode code) {
		JDialog dialog = new JDialog(this, "Microsoft login", true);
		dialog.setLayout(new BorderLayout(8, 8));
		JPanel center = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.gridx = 0;
		gbc.gridy = 0;
		center.add(new JLabel("1. Open:"), gbc);
		gbc.gridx = 1;
		JTextField urlField = new JTextField(code.verificationUri, 24);
		urlField.setEditable(false);
		center.add(urlField, gbc);
		gbc.gridx = 0;
		gbc.gridy = 1;
		center.add(new JLabel("2. Enter code:"), gbc);
		gbc.gridx = 1;
		JTextField codeField = new JTextField(code.userCode, 12);
		codeField.setEditable(false);
		codeField.setFont(codeField.getFont().deriveFont(java.awt.Font.BOLD, 18f));
		center.add(codeField, gbc);
		dialog.add(center, BorderLayout.CENTER);
		JButton openButton = new JButton("Open browser");
		openButton.addActionListener(e -> {
			try {
				java.awt.Desktop.getDesktop().browse(new java.net.URI(code.verificationUri));
			} catch (Exception ignored) {
			}
		});
		dialog.add(openButton, BorderLayout.SOUTH);
		dialog.pack();
		dialog.setLocationRelativeTo(this);
		Thread poll = new Thread(() -> {
			try {
				MicrosoftAuth.Session session = MicrosoftAuth.pollForApproval(data.azureClientId, code, 10 * 60 * 1000L);
				SwingUtilities.invokeLater(() -> {
					dialog.dispose();
					if (session == null) {
						JOptionPane.showMessageDialog(this, "Login timed out. Try again.", "Timeout", JOptionPane.WARNING_MESSAGE);
						return;
					}
					ProfileStore.Account account = new ProfileStore.Account();
					account.type = "microsoft";
					account.name = session.name;
					account.uuid = session.uuid;
					account.accessToken = session.accessToken;
					account.refreshToken = session.refreshToken;
					upsert(account);
					log("Microsoft login OK: " + session.name);
				});
			} catch (Exception e) {
				SwingUtilities.invokeLater(() -> {
					dialog.dispose();
					JOptionPane.showMessageDialog(this, "Microsoft login failed:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
				});
			}
		}, "nocturne-ms-poll");
		poll.setDaemon(true);
		poll.start();
		dialog.setVisible(true);
	}

	private void upsert(ProfileStore.Account account) {
		data.accounts.removeIf(a -> account.name.equals(a.name));
		data.accounts.add(account);
		data.selectedAccount = account.name;
		ProfileStore.save(data);
		refreshAccounts();
	}

	// ---------------- settings ----------------

	private JPanel buildSettingsCard() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(6, 6, 6, 6);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel.add(new JLabel("Game folder:"), gbc);
		gbc.gridx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		gameDirField.setText(data.gameDir);
		panel.add(gameDirField, gbc);
		gbc.gridx = 2;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 0;
		JButton browse = new JButton("Browse");
		browse.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser(gameDirField.getText());
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				gameDirField.setText(chooser.getSelectedFile().getAbsolutePath());
			}
		});
		panel.add(browse, gbc);
		gbc.gridx = 0;
		gbc.gridy = 1;
		panel.add(new JLabel("Azure client ID:"), gbc);
		gbc.gridx = 1;
		gbc.gridwidth = 2;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		azureField.setText(data.azureClientId);
		azureField.setToolTipText("Advanced: your own Azure app registration client ID for Microsoft login.");
		panel.add(azureField, gbc);
		gbc.gridx = 1;
		gbc.gridy = 2;
		gbc.gridwidth = 2;
		JButton save = new JButton("Save settings");
		save.addActionListener(e -> {
			data.gameDir = gameDirField.getText().trim();
			data.loaderVersion = loaderField.getText().trim();
			data.azureClientId = azureField.getText().trim();
			ProfileStore.save(data);
			JOptionPane.showMessageDialog(this, "Settings saved.", "Nocturne", JOptionPane.INFORMATION_MESSAGE);
		});
		panel.add(save, gbc);
		gbc.gridy = 3;
		gbc.weighty = 1;
		panel.add(new JLabel(""), gbc);
		return panel;
	}
}
