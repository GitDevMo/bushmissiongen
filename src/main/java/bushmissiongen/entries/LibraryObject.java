package bushmissiongen.entries;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bushmissiongen.messages.ErrorMessage;
import bushmissiongen.messages.Message;

public class LibraryObject {
	public String mName;
	private String mField;
	private String mString;

	public String mdlGUID = "";
	public String latlon = "";
	public String altitude = "";
	public String heading = "0.000";
	public String scale = "1.000";
	public String agl = "";

	public String triggerId = "";
	public String triggerGUID = "";

	public LibraryObject() {
	}

	public LibraryObject(String name, String field, String string) {
		mName = name;
		mField = field;
		mString = string;
	}

	public Message handle() {
		String[] split = mString.split("#");

		// Coordinate validation
		if (split.length != 5) {
			return new ErrorMessage("Wrong format for libaryObject:\n\n" + mField + "=" + mString);
		}

		mdlGUID = split[0];
		latlon = split[1];
		altitude = split[2];
		heading = split[3];
		scale = split[4];

		Pattern pattern1 = Pattern.compile("^\\d+(\\.\\d{3})$");

		boolean res2 = pattern1.matcher(heading).find();
		boolean res3 = pattern1.matcher(scale).find();
		if (!res2 || !res3) {
			return new ErrorMessage("Wrong format for libaryObject:\n\n" + mField + "=" + mString);
		}

		Pattern pattern5 = Pattern.compile("^(\\d+\\.\\d{3})([A-Z]+)?$");
		Matcher matcher5 = pattern5.matcher(altitude);
		if (matcher5.find()) {
			altitude = matcher5.group(1);
			if (matcher5.group(2) != null) {
				String altMode = matcher5.group(2);
				if (altMode.equals("AGL")) {
					agl = "True";
				} else if (altMode.equals("AMSL")) {
					agl = "False";
				}
			}
		}

		// Coordinate transformation
		MissionEntry meTest = new MissionEntry();
		Message msgLatlon = meTest.setLatlon(latlon);
		if (msgLatlon != null) {
			return msgLatlon;
		}
		latlon = meTest.latlon;

		return null;
	}
}
