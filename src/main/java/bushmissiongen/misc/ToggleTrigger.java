package bushmissiongen.misc;

public class ToggleTrigger {
	public String[] mTriggerList = null;
	public boolean mActivate = false;
	public String text = "";

	public String procWave = "";
	public String procText = "";
	public String procTextID = "";

	public ToggleTrigger(boolean activate, String str, String... triggerList) {
		mActivate = activate;
		text = str;
		mTriggerList = triggerList;
	}
}
