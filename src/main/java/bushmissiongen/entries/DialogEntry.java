package bushmissiongen.entries;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bushmissiongen.messages.ErrorMessage;
import bushmissiongen.messages.Message;

public class DialogEntry {
	public String mName;
	private String mField;
	private String mString;

	// SPLIT LEN = 2
	public String text = "";
	public String latlon = "";

	// SPLIT LEN = 6 or more
	public String heading = "0.000";
	public String length = "3500.000";
	public String width = "3500.000";
	public String height = "10000.000";
	public String agl = "";

	public String delay = "0.000";

	public boolean exit = false;

	public String procWave = "";
	public String procText = "";
	public String procTextID = "";

	public DialogEntry() {
	}

	public DialogEntry(String name, String field, String string) {
		mName = name;
		mField = field;
		mString = string;
	}

	public Message handle() {
		String[] split = mString.split("#");

		// Text and coordinate validation
		if (split.length < 2 || split[0].trim().length()==0 || split[1].trim().length()==0) {
			return new ErrorMessage("Wrong format for dialogEntry:\n\n" + mField + "=" + mString);
		}

		text = split[0];
		latlon = split[1];

		if (split.length>=6) {
			heading = split[2];
			length = split[3];
			width = split[4];
			height = split[5];

			Pattern pattern1 = Pattern.compile("^\\d+(\\.\\d{3})$");

			boolean res2 = pattern1.matcher(heading).find();
			boolean res3 = pattern1.matcher(length).find();
			boolean res4 = pattern1.matcher(width).find();
			if (!res2 || !res3 || !res4) {
				return new ErrorMessage("Wrong format for dialogEntry:\n\n" + mField + "=" + mString);
			}

			Pattern pattern5 = Pattern.compile("^(\\d+\\.\\d{3})([A-Z]+)?$");
			Matcher matcher5 = pattern5.matcher(height);
			if (matcher5.find()) {
				height = matcher5.group(1);
				if (matcher5.group(2) != null) {
					String altMode = matcher5.group(2);
					if (altMode.equals("AGL")) {
						agl = "True";
					} else if (altMode.equals("AMSL")) {
						agl = "False";
					}
				}
			}			
		}

		if (split.length>=7) {
			delay = split[6];

			Pattern pattern1 = Pattern.compile("^\\d+(\\.\\d{3})$");
			boolean res6 = pattern1.matcher(delay).find();
			if (!res6) {
				return new ErrorMessage("Wrong format for dialogEntry:\n\n" + mField + "=" + mString);
			}
		}

		// Coordinate transformation
		MissionEntry meTest = new MissionEntry();
		Message msgLatlon = meTest.setLatlon(latlon);
		if (msgLatlon != null) {
			return msgLatlon;
		}
		latlon = meTest.latlon;

		exit = mField.toLowerCase().contains("exit");

		return null;
	}
}
