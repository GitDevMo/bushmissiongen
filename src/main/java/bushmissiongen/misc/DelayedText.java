package bushmissiongen.misc;

public class DelayedText {
	public String text  = "";
	public String textID = "";
	public String delay = "";

	public String procWave = "";
	public String procText = "";
	public String procTextID = "";

	public String start = "0.000";

	public DelayedText(String useText, String useDelay) {
		text = useText;
		delay = useDelay;
	}

	public DelayedText(String useText, String useDelay, String useStart) {
		text = useText;
		delay = useDelay;
		start = useStart;
	}
}
