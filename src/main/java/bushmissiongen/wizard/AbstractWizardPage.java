package bushmissiongen.wizard;

import java.awt.LayoutManager;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JPanel;

public abstract class AbstractWizardPage extends JPanel {
	private static final long serialVersionUID = 1000143453263604518L;

	private WizardController wizardController;

	protected Map<String, String> values = new HashMap<>();

	public AbstractWizardPage(LayoutManager layout, boolean isDoubleBuffered) {
		super(layout, isDoubleBuffered);
	}

	public AbstractWizardPage(LayoutManager layout) {
		super(layout);
	}

	public AbstractWizardPage(boolean isDoubleBuffered) {
		super(isDoubleBuffered);
	}

	public AbstractWizardPage() {
		super();
	}

	void setWizardController(WizardController wizardController) {
		this.wizardController = wizardController;
	}

	public void updateWizardButtons() {
		if (wizardController != null) {
			wizardController.updateButtons();
		}
	}

	protected abstract AbstractWizardPage getNextPage();

	protected abstract boolean isCancelAllowed();

	protected abstract boolean isPreviousAllowed();

	protected abstract boolean isNextAllowed();

	protected abstract boolean isFinishAllowed();

	public Map<String, String> getValues() {
		return values;
	}
}
