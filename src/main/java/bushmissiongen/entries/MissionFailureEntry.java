package bushmissiongen.entries;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bushmissiongen.messages.ErrorMessage;
import bushmissiongen.messages.Message;

public class MissionFailureEntry {
	public String mName;
	private String mField;
	private String mString;

	public String latlon = "";

	public String heading = "";
	public String length = "";
	public String width = "";
	public String height = "";
	public String agl = "";

	public String value1 = "";
	public String value2 = "";
	public String value3 = "";

	public String triggerId = "";
	public String triggerGUID = "";

	public boolean exit = false;

	public MissionFailureEntryMode currentMode = null;

	public enum MissionFailureEntryMode {
		AREA,
		ALTITUDE,
		SPEED,
		ALTITUDE_AND_SPEED,
		TIME,
		FORMULA
	};

	public MissionFailureEntry(String name, String field, String string) {
		mName = name;
		mField = field;
		mString = string;
	}

	public Message handle() {
		if (mField.toLowerCase().endsWith("area")) {
			currentMode = MissionFailureEntryMode.AREA;
		} else if (mField.toLowerCase().endsWith("altitudespeed") || mField.toLowerCase().endsWith("altitudeandspeed")) {
			currentMode = MissionFailureEntryMode.ALTITUDE_AND_SPEED;
		} else if (mField.toLowerCase().endsWith("altitude")) {
			currentMode = MissionFailureEntryMode.ALTITUDE;
		} else if (mField.toLowerCase().endsWith("speed")) {
			currentMode = MissionFailureEntryMode.SPEED;
		} else if (mField.toLowerCase().endsWith("time")) {
			currentMode = MissionFailureEntryMode.TIME;
		} else if (mField.toLowerCase().endsWith("formula")) {
			currentMode = MissionFailureEntryMode.FORMULA;
		} else {
			return new ErrorMessage("Wrong format for missionFailure:\n\n" + mField + "=" + mString);
		}

		String[] split = mString.split("#");

		switch(currentMode) {
		case AREA:
			if (split.length >= 5) {
				latlon = split[0].trim();
				heading = split[1].trim();
				length = split[2].trim();
				width = split[3].trim();
				height = split[4].trim();

				Pattern patternArea = Pattern.compile("^(\\d+\\.\\d{3})([A-Z]+)?$");
				Matcher matcherArea = patternArea.matcher(height);
				if (matcherArea.find()) {
					height = matcherArea.group(1);
					if (matcherArea.group(2) != null) {
						String altMode = matcherArea.group(2);
						if (altMode.equals("AGL")) {
							agl = "True";
						} else if (altMode.equals("AMSL")) {
							agl = "False";
						}
					}
				} else {
					return new ErrorMessage("Wrong format for missionFailureArea:\n\n" + mField + "=" + mString);
				}

				if (split.length == 6) {
					value2 = split[5].trim();
				} else if (split.length > 6) {
					return new ErrorMessage("Wrong format for missionFailureArea:\n\n" + mString);
				}
			} else {
				return new ErrorMessage("Wrong format for missionFailureArea:\n\n" + mString);
			}

			// Coordinate transformation
			MissionEntry meTest = new MissionEntry();
			Message msgLatlon = meTest.setLatlon(latlon);
			if (msgLatlon != null) {
				return msgLatlon;
			}
			latlon = meTest.latlon;
			break;
		case ALTITUDE:
			if (split.length >= 1) {
				Pattern pattern1 = Pattern.compile("^(\\d+\\.\\d{3})([A-Z]+)?$");
				Matcher matcher1 = pattern1.matcher(split[0]);
				if (matcher1.find()) {
					value1 = matcher1.group(1);
					if (matcher1.group(2) != null) {
						String altMode = matcher1.group(2);
						if (altMode.equals("AGL")) {
							agl = "True";
						} else if (altMode.equals("AMSL")) {
							agl = "False";
						}
					}
				} else {
					return new ErrorMessage("Wrong format for missionFailureAltitude:\n\n" + mField + "=" + mString);
				}

				if (split.length == 2) {
					value2 = split[1].trim();
				} else if (split.length > 2) {
					return new ErrorMessage("Wrong format for missionFailureAltitude:\n\n" + mString);
				}
			} else {
				return new ErrorMessage("Wrong format for missionFailureAltitude:\n\n" + mField + "=" + mString);
			}
			break;
		case SPEED:
			if (split.length >= 1) {
				value1 = split[0].trim();

				Pattern pattern2 = Pattern.compile("^\\d+(\\.\\d{3})$");
				if (!pattern2.matcher(value1).find()) {
					return new ErrorMessage("Wrong format for missionFailureSpeed:\n\n" + mField + "=" + mString);
				}

				if (split.length == 2) {
					value2 = split[1].trim();
				} else if (split.length > 2) {
					return new ErrorMessage("Wrong format for missionFailureSpeed:\n\n" + mString);
				}
			} else {
				return new ErrorMessage("Wrong format for missionFailureSpeed:\n\n" + mField + "=" + mString);
			}
			break;
		case ALTITUDE_AND_SPEED:
			if (split.length >= 2) {
				Pattern pattern3a = Pattern.compile("^(\\d+\\.\\d{3})([A-Z]+)?$");
				Matcher matcher3a = pattern3a.matcher(split[0]);
				if (matcher3a.find()) {
					value1 = matcher3a.group(1);
					if (matcher3a.group(2) != null) {
						String altMode = matcher3a.group(2);
						if (altMode.equals("AGL")) {
							agl = "True";
						} else if (altMode.equals("AMSL")) {
							agl = "False";
						}
					}
				} else {
					return new ErrorMessage("Wrong format for missionFailureAltitudeAndSpeed:\n\n" + mField + "=" + mString);
				}
				Pattern pattern3b = Pattern.compile("^(\\d+\\.\\d{3})$");
				Matcher matcher3b = pattern3b.matcher(split[1]);
				if (matcher3b.find()) {
					value2 = matcher3b.group(1);
				} else {
					return new ErrorMessage("Wrong format for missionFailureAltitudeAndSpeed:\n\n" + mField + "=" + mString);
				}

				if (split.length == 3) {
					value3 = split[2].trim();
				} else if (split.length > 3) {
					return new ErrorMessage("Wrong format for missionFailureAltitudeAndSpeed:\n\n" + mString);
				}
			} else {
				return new ErrorMessage("Wrong format for missionFailureAltitudeAndSpeed:\n\n" + mField + "=" + mString);
			}
			break;
		case TIME:
			if (split.length >= 1) {
				value1 = split[0].trim();

				Pattern pattern4= Pattern.compile("^\\d+(\\.\\d{3})$");
				if (!pattern4.matcher(value1).find()) {
					return new ErrorMessage("Wrong format for missionFailureTime:\n\n" + mField + "=" + mString);
				}

				if (split.length == 2) {
					value2 = split[1].trim();
				} else if (split.length > 2) {
					return new ErrorMessage("Wrong format for missionFailureTime:\n\n" + mString);
				}
			} else {
				return new ErrorMessage("Wrong format for missionFailureTime:\n\n" + mField + "=" + mString);
			}

			break;
		case FORMULA:
			if (split.length != 2) {
				return new ErrorMessage("Wrong format for missionFailureFormula:\n\n" + mField + "=" + mString);
			}
			value1 = split[0].trim();
			value2 = split[1].trim();

			break;
		}

		exit = mField.toLowerCase().contains("exit");

		return null;
	}
}
