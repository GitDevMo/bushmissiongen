package bushmissiongen.entries;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bushmissiongen.messages.ErrorMessage;
import bushmissiongen.messages.Message;

public class FailureEntry {
	private String mField;
	private String mString;

	public String health = "";
	public String latlon = "";
	public String formula = "";

	public String heading = "";
	public String length = "";
	public String width = "";
	public String height = "";
	public String speed = "";
	public String agl = "";

	public String system = "";
	public String systemIndex = "0";

	public boolean exit = false;

	public FailureEntryMode currentMode = null;

	public enum FailureEntryMode {
		AREA,
		ALTITUDE,
		SPEED,
		ALTITUDE_AND_SPEED,
		FORMULA
	};

	public FailureEntry(String field, String string, String systemName, String index, boolean exitValue, FailureEntryMode mode) {
		mField = field;
		mString = string;
		system = systemName;
		systemIndex = index;
		exit = exitValue;
		currentMode = mode;
	}

	public Message handle() {		
		String[] split = mString.split("#");

		// Text validation
		if (split.length < 2 || split[0].trim().length()==0 || split[1].trim().length()==0) {
			return new ErrorMessage("Wrong format for failureEntry:\n\n" + mField + "=" + mString);
		}

		health = split[0];

		if (currentMode == FailureEntryMode.AREA) {
			latlon = split[1];
		} else if (currentMode == FailureEntryMode.ALTITUDE_AND_SPEED) {
			if (split.length < 3 || split[2].trim().length()==0) {
				return new ErrorMessage("Wrong format for failureEntry:\n\n" + mField + "=" + mString);
			}

			height = split[1];
			speed = split[2];
		} else if (currentMode == FailureEntryMode.ALTITUDE) {
			height = split[1];
		} else if (currentMode == FailureEntryMode.SPEED) {
			speed = split[1];
		} else if (currentMode == FailureEntryMode.FORMULA) {
			formula = split[1];
		} else {
			return new ErrorMessage("Wrong format for failureEntry:\n\n" + mField + "=" + mString);
		}

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
				return new ErrorMessage("Wrong format for failureEntry:\n\n" + mField + "=" + mString);
			}
		}

		// Height validation
		if (height.length()>0) {
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
				return new ErrorMessage("Wrong format for failureEntry:\n\n" + mField + "=" + mString);
			}
		}

		// Speed validation
		if (speed.length()>0) {
			Pattern pattern1 = Pattern.compile("^(\\d+\\.\\d{3})$");
			Matcher matcher1 = pattern1.matcher(speed);
			if (!matcher1.find()) {
				return new ErrorMessage("Wrong format for failureEntry:\n\n" + mField + "=" + mString);
			}
		}

		// Coordinate transformation
		if (latlon.length() > 0) {
			MissionEntry meTest = new MissionEntry();
			Message msgLatlon = meTest.setLatlon(latlon);
			if (msgLatlon != null) {
				return msgLatlon;
			}
			latlon = meTest.latlon;
		}

		return null;
	}
}
