package bushmissiongen.wizard.pages;

import java.awt.FlowLayout;
import java.awt.Label;
import java.util.Map;

import bushmissiongen.wizard.AbstractWizardPage;

@SuppressWarnings("serial")
public class EndPage extends AbstractWizardPage {
	public EndPage(Map<String, String> defaultValues) {
		setLayout(new FlowLayout()); 
		add(new Label("Press Finish to continue.."));
	}

	@Override
	protected AbstractWizardPage getNextPage() {
		return null;
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
		return false;
	}

	@Override
	protected
	boolean isFinishAllowed() {
		return true;
	}
}
