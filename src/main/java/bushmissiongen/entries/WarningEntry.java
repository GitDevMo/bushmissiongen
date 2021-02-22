package bushmissiongen.entries;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bushmissiongen.messages.ErrorMessage;
import bushmissiongen.messages.Message;

public class WarningEntry {
	private String mField;
	private String mString;

	public String text = "";
	public String height = "";
	public String speed = "";
	public String formula = "";
	public String agl = "";

	public WarningEntryMode currentMode = null;

	public enum WarningEntryMode {
		ALTITUDE,
		SPEED,
		ALTITUDE_AND_SPEED,
		FORMULA
	};

	public WarningEntry(String field, String string, WarningEntryMode mode) {
		mField = field;
		mString = string;
		currentMode = mode;
	}

	public Message handle() {		
		String[] split = mString.split("#");

		// Text validation
		if (split.length < 2 || split[0].trim().length()==0 || split[1].trim().length()==0) {
			return new ErrorMessage("Wrong format for WarningEntry:\n\n" + mField + "=" + mString);
		}

		text = split[0];

		if (currentMode == WarningEntryMode.ALTITUDE) {
			height = split[1];
		} else if (currentMode == WarningEntryMode.SPEED) {
			speed = split[1];
		} else if (currentMode == WarningEntryMode.ALTITUDE_AND_SPEED) {
			// Text validation
			if (split.length < 3 || split[2].trim().length()==0) {
				return new ErrorMessage("Wrong format for WarningEntry:\n\n" + mField + "=" + mString);
			}

			height = split[1];
			speed = split[2];
		} else if (currentMode == WarningEntryMode.FORMULA) {
			formula = split[1];
		} else {
			return new ErrorMessage("Wrong format for WarningEntry:\n\n" + mField + "=" + mString);
		}

		// Height validation
		if (height.length() > 0) {
			Pattern pattern1 = Pattern.compile("^(\\d+\\.\\d{3})([A-Z]+)?$");
			Matcher matcher1 = pattern1.matcher(height);
			if (matcher1.find()) {
				height = matcher1.group(1);
				if (matcher1.group(2) != null) {
					String altMode = matcher1.group(2);
					if (altMode.equals("AGL")) {
						agl = "True";
					} else if (altMode.equals("AMSL")) {
						agl = "False";
					}
				}
			} else {
				return new ErrorMessage("Wrong format for WarningEntry:\n\n" + mField + "=" + mString);
			}
		}

		// Speed validation
		if (speed.length() > 0) {
			Pattern pattern1 = Pattern.compile("^(\\d+\\.\\d{3})$");
			Matcher matcher1 = pattern1.matcher(speed);
			if (!matcher1.find()) {
				return new ErrorMessage("Wrong format for WarningEntry:\n\n" + mField + "=" + mString);
			}
		}

		return null;
	}
}
