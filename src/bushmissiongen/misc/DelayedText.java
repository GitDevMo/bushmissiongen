package bushmissiongen.misc;

public class DelayedText {
	public String text  = "";
	public String delay = "";

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
