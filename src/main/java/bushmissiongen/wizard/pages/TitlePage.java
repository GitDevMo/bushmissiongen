package bushmissiongen.wizard.pages;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JComboBox;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import bushmissiongen.misc.SimData;
import bushmissiongen.wizard.AbstractWizardPage;

@SuppressWarnings("serial")
public class TitlePage extends AbstractWizardPage {

	private final AbstractWizardPage nextPage;
	@SuppressWarnings("unused")
	private Map<String, String> mDefaultValues;

	private final JTextArea authorField = new JTextArea(1, 30);
	private final JTextArea titleField = new JTextArea(1, 30);
	private final JTextArea projectField = new JTextArea(1, 30);
	private final JTextArea locationField = new JTextArea(1, 30);
	private final JTextArea introField = new JTextArea(5,30);
	private final JTextArea headingField = new JTextArea(1,5);
	private final JComboBox<String> planeCombo;
	private final JComboBox<String> weatherCombo;

	public TitlePage(Map<String, String> defaultValues) {
		mDefaultValues = defaultValues;
		nextPage = new EndPage(defaultValues);

		setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();

		int currentRow = 0;

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridwidth = 1;
		c.weighty = 1;

		c.weightx = 0.10;
		c.gridx = 0;
		c.gridy = currentRow;
		add(new Label("Author:"), c);
		c.weightx = 0.5;
		c.gridx = 1;
		c.gridy = currentRow++;
		c.anchor=GridBagConstraints.EAST;
		add(authorField, c);

		c.weightx = 0.10;
		c.gridx = 0;
		c.gridy = currentRow;
		add(new Label("Title:"), c);
		c.weightx = 0.5;
		c.gridx = 1;
		c.gridy = currentRow++;
		c.anchor=GridBagConstraints.EAST;
		add(titleField, c);

		c.weightx = 0.10;
		c.gridx = 0;
		c.gridy = currentRow;
		add(new Label("Project:"), c);
		c.weightx = 0.5;
		c.gridx = 1;
		c.gridy = currentRow++;
		c.anchor=GridBagConstraints.EAST;
		add(projectField, c);

		c.weightx = 0.10;
		c.gridx = 0;
		c.gridy = currentRow;
		add(new Label("Location:"), c);
		c.weightx = 0.5;
		c.gridx = 1;
		c.gridy = currentRow++;
		c.anchor=GridBagConstraints.EAST;
		add(locationField, c);

		c.weightx = 0.10;
		c.gridx = 0;
		c.gridy = currentRow;
		add(new Label("Heading:"), c);
		c.weightx = 0.5;
		c.gridx = 1;
		c.gridy = currentRow++;
		c.anchor=GridBagConstraints.EAST;
		add(headingField, c);

		c.weightx = 0.10;
		c.weighty = 3;
		c.gridx = 0;
		c.gridy = currentRow;
		add(new Label("Intro (HTML):"), c);
		c.weightx = 0.5;
		c.weighty = 3;
		c.gridx = 1;
		c.gridy = currentRow++;
		c.fill=GridBagConstraints.HORIZONTAL|GridBagConstraints.VERTICAL;
		c.anchor=GridBagConstraints.EAST;
		introField.setSize(introField.getPreferredSize());
		add(introField, c);

		c.weightx = 0.10;
		c.gridx = 0;
		c.gridy = currentRow;
		add(new Label("Plane:"), c);
		c.weightx = 0.5;
		c.gridx = 1;
		c.gridy = currentRow++;
		c.fill=GridBagConstraints.HORIZONTAL;
		c.anchor=GridBagConstraints.EAST;
		List<String> planesList = new ArrayList<>(Arrays.asList(SimData.planes));
		planesList.addAll(SimData.encryptedOfficial);
		planesList.add(SimData.THIRD_PARTY_PLANE);
		planeCombo = new JComboBox<String>(planesList.toArray(String[]::new));
		planeCombo.setSize(planeCombo.getPreferredSize());
		add(planeCombo, c);

		c.weightx = 0.10;
		c.gridx = 0;
		c.gridy = currentRow;
		add(new Label("Weather:"), c);
		c.weightx = 0.5;
		c.gridx = 1;
		c.gridy = currentRow++;
		c.fill=GridBagConstraints.HORIZONTAL;
		c.anchor=GridBagConstraints.EAST;
		weatherCombo = new JComboBox<String>(SimData.weatherTypes);
		weatherCombo.setSize(weatherCombo.getPreferredSize());
		add(weatherCombo, c);

		DocumentListener dl = new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				updateWizardButtons();
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				updateWizardButtons();
			}

			@Override
			public void changedUpdate(DocumentEvent arg0) {
				updateWizardButtons();
			}
		};

		projectField.getDocument().addDocumentListener(dl);
		headingField.getDocument().addDocumentListener(dl);

		if (defaultValues.containsKey("heading")) {
			headingField.setText(defaultValues.get("heading"));
		}
	}

	@Override
	protected AbstractWizardPage getNextPage() {
		values.put("author", authorField.getText().trim());
		values.put("title", titleField.getText().trim());
		values.put("project", projectField.getText().trim());
		values.put("location", locationField.getText().trim());
		values.put("heading", headingField.getText().trim());
		values.put("intro", introField.getText().trim());
		values.put("plane", planeCombo.getItemAt(planeCombo.getSelectedIndex()));
		values.put("weather", weatherCombo.getItemAt(weatherCombo.getSelectedIndex()));
		return nextPage;
	}

	@Override
	protected boolean isCancelAllowed() {
		return true;
	}

	@Override
	protected boolean isPreviousAllowed() {
		return true;
	}

	@Override
	protected boolean isNextAllowed() {
		{
			// Fulfill FS2020 package name validation
			Pattern pattern = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+){1,}$");
			Matcher matcher = pattern.matcher(projectField.getText());
			if (!matcher.find()) {
				projectField.setBackground(Color.RED);
				projectField.setToolTipText("Project names must be in the form 'aaa-bbb-...-xxx' and only contain lower case letters or digits.");
				return false;
			}
		}

		if (!headingField.getText().isEmpty()) {
			Pattern pattern = Pattern.compile("^[0-9]+$");
			Matcher matcher = pattern.matcher(headingField.getText());
			if (!matcher.find()) {
				headingField.setBackground(Color.RED);
				headingField.setToolTipText("Must be a valid number! Leave empty for 0 degrees.");
				return false;
			}
		}

		projectField.setBackground(Color.WHITE);
		headingField.setBackground(Color.WHITE);
		projectField.setToolTipText("");
		headingField.setToolTipText("");

		return true;
	}

	@Override
	protected boolean isFinishAllowed() {
		return false;
	}
}
