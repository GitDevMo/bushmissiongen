package bushmissiongen.wizard;

import java.awt.Container;

import javax.swing.AbstractButton;

public interface IWizard {
	Container getWizardPageContainer();

	AbstractButton getCancelButton();

	AbstractButton getPreviousButton();

	AbstractButton getNextButton();

	AbstractButton getFinishButton();
}
