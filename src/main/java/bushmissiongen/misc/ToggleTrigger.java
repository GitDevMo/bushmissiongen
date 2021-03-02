package bushmissiongen.misc;

public class ToggleTrigger {
	public String[] mTriggerList = null;
	public boolean mActivate = false;

	public ToggleTrigger(boolean activate, String... triggerList) {
		mActivate = activate;
		mTriggerList = triggerList;
	}
}
