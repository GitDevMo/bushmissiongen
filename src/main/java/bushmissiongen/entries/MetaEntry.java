package bushmissiongen.entries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bushmissiongen.messages.ErrorMessage;
import bushmissiongen.messages.Message;
import bushmissiongen.misc.DelayedText;
import bushmissiongen.misc.Localization;
import bushmissiongen.misc.ToggleTrigger;

public class MetaEntry extends GenericEntry {
	public String author = "";
	public String project = "";
	public String version = "";

	public String title = "";
	public String location = "";
	public String description = "";
	public List<String> tooltips = new ArrayList<>();
	public String intro = "";

	public List<Localization> localizations = new ArrayList<Localization>();

	public String titleID = "?";
	public String locationID = "?";
	public String descriptionID = "?";
	public List<String> tooltipsID = new ArrayList<>();
	public String introID = "?";

	public String lat = "";
	public String lon = "";
	public String pitch = "";
	public String bank = "";
	public String heading = "";
	public String plane = "";

	public String season = "";
	public String year = "";
	public String day = "";
	public String hours = "";
	public String minutes = "";
	public String seconds = "";

	// Landing challenge
	public String missionType = "";
	public String challengeType = "";
	public String velocity = "";
	public String flapsHandle = "050.00";
	public String leftFlap = "050.00";
	public String rightFlap = "050.00";
	public String elevatorTrim = "050.00";
	public String noGear = "";

	public static final String LandingChallenge_DefaultFile = "Missions\\Asobo\\LandingChallenges\\default\\LandingChallenge_Private";
	public static final String LandingChallenge_PrivateTemplate = "land_private_template";
	public static final String LandingChallenge_NoGearTemplate = "land_nogear_template";

	public List<String> requiredFields = new ArrayList<>();

	public MetaEntry() {
		requiredFields.add("author");
		requiredFields.add("project");
		requiredFields.add("version");

		requiredFields.add("title");
		requiredFields.add("location");
		requiredFields.add("description");
		requiredFields.add("intro");

		requiredFields.add("latitude");
		requiredFields.add("longitude");
		requiredFields.add("altitude");
		requiredFields.add("pitch");
		requiredFields.add("bank");
		requiredFields.add("heading");
		requiredFields.add("plane");

		requiredFields.add("season");
		requiredFields.add("year");
		requiredFields.add("day");
		requiredFields.add("hours");
		requiredFields.add("minutes");
		requiredFields.add("seconds");
	}

	// Optional
	public String sdkPath = "";
	public String uniqueApImages = "";
	public String poiSpeech = "";
	public String poiSpeechBefore = "";
	public String simFile = "runway.FLT";
	public String fuelPercentage = "100";
	public String parkingBrake = "0.00";
	public String tailNumber = "";
	public String airlineCallSign = "";
	public String flightNumber = "";
	public String appendHeavy = "False";
	public String weather = "";
	public String pilot = "";
	public String multiPlayer = "1";
	public String showVfrMap = "True";
	public String showNavLog = "True";
	public String enableRefueling = "";
	public String enableAtc = "";
	public String enableChecklist = "";
	public String enableObjectives = "";
	public String requireEnginesOff = "";
	public String requireBatteryOff = "";
	public String requireAvionicsOff = "";
	public String useAGL = "";
	public String useOneShotTriggers = "True";
	public String standardAirportExitAreaSideLength = "";
	public String standardEnterAreaSideLength = "";

	public List<DelayedText> introSpeeches = new ArrayList<>();
	public List<String> coPilots = new ArrayList<>();
	public List<DialogEntry> dialogEntries = new ArrayList<>();
	public List<WarningEntry> warningsEntries = new ArrayList<>();
	public List<FailureEntry> failureEntries = new ArrayList<>();
	public List<MissionFailureEntry> missionFailures = new ArrayList<>();
	public Map<String, List<DelayedText>> finishedEntries = new HashMap<>();
	public Map<String, ToggleTrigger> toggleTriggers = new HashMap<>();

	private String getFormattedLocationInDegreeLat(double latitude) {
		try {
			int latSeconds = (int) Math.round(latitude * 3600);
			int latDegrees = latSeconds / 3600;
			latSeconds = Math.abs(latSeconds % 3600);
			int latMinutes = latSeconds / 60;
			latSeconds %= 60;

			String latDegree = latDegrees >= 0 ? "N" : "S";

			return latDegree + Math.abs(latDegrees) + "° " + latMinutes + "' " + latSeconds
					+ "\"";
		} catch (Exception e) {
			return null;
		}
	}

	private String getFormattedLocationInDegreeLon(double longitude) {
		try {
			int longSeconds = (int) Math.round(longitude * 3600);
			int longDegrees = longSeconds / 3600;
			longSeconds = Math.abs(longSeconds % 3600);
			int longMinutes = longSeconds / 60;
			longSeconds %= 60;
			String lonDegrees = longDegrees >= 0 ? "E" : "W";

			return lonDegrees + Math.abs(longDegrees) + "° " + longMinutes + "' " + longSeconds
					+ "\"";
		} catch (Exception e) {
			return null;
		}
	}

	public Message setLat(String string) {
		// Check format!

		{
			// 12° 17' 49,45" N  (Little Navmap)
			Pattern pattern = Pattern.compile("^(\\d+°)\\s?(\\d+')\\s?(\\d+[,]?[\\d]+?\")\\s?([NS])");
			Matcher matcher = pattern.matcher(string);
			if (this.lat.isEmpty() && matcher.find())
			{
				this.lat = matcher.group(4) + matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3).replace(",", ".");
			} 
		}

		{
			// 8°07'34.4"N 
			Pattern pattern = Pattern.compile("^(\\d+°)\\s?(\\d+')\\s?(\\d+[.]?[\\d]+?\")\\s?([NS])");
			Matcher matcher = pattern.matcher(string);
			if (this.lat.isEmpty() && matcher.find())
			{
				this.lat = matcher.group(4) + matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3);
			} 
		}

		{
			// N8°07'34.4"
			Pattern pattern = Pattern.compile("^([NS]\\d+°)\\s?(\\d+')\\s?(\\d+[.]?[\\d]+?\")");
			Matcher matcher = pattern.matcher(string);
			if (this.lat.isEmpty() && matcher.find())
			{
				this.lat = matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3);
			}
		}

		{
			// N65°18.25'
			Pattern pattern = Pattern.compile("([NS]\\d+°)\\s?(\\d+[.]?[\\d]+?')");
			Matcher matcher = pattern.matcher(string);
			if (this.lat.isEmpty() && matcher.find())
			{
				this.lat = matcher.group(1) + " " + matcher.group(2);
			}
		}

		// 64.412136
		{
			Pattern pattern = Pattern.compile("^([\\-]?\\d+\\.\\d+)");
			Matcher matcher = pattern.matcher(string);
			if (this.lat.isEmpty() && matcher.find())
			{
				String val = getFormattedLocationInDegreeLat(Double.parseDouble(matcher.group(1)));
				if (val == null) {
					return new ErrorMessage("Bad decimal degree: " + string);
				}
				this.lat = val;
			}
		}

		if (this.lat.isEmpty()) {
			return new ErrorMessage("Bad lat: " + string);
		}

		return null;
	}

	public Message setLon(String string) {
		// Check format!

		{
			// 16° 44' 34,86" E  (Little Navmap)
			Pattern pattern = Pattern.compile("^(\\d+°)\\s?(\\d+')\\s?(\\d+[,]?[\\d]+?\")\\s?([WE])");
			Matcher matcher = pattern.matcher(string);
			if (this.lon.isEmpty() && matcher.find())
			{
				this.lon = matcher.group(4) + matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3).replace(",", ".");
			} 
		}

		{
			// 98°55'22.0"E
			Pattern pattern = Pattern.compile("^(\\d+°)\\s?(\\d+')\\s?(\\d+[.]?[\\d]+?\")\\s?([WE])");
			Matcher matcher = pattern.matcher(string);
			if (this.lon.isEmpty() && matcher.find())
			{
				this.lon = matcher.group(4) + matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3);
			}
		}

		{
			// E98°55'22.0"
			Pattern pattern = Pattern.compile("^([WE]\\d+°)\\s?(\\d+')\\s?(\\d+[.]?[\\d]+?\")");
			Matcher matcher = pattern.matcher(string);
			if (this.lon.isEmpty() && matcher.find())
			{
				this.lon = matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3);
			}
		}

		{
			// E17°58.51'
			Pattern pattern = Pattern.compile("^([WE]\\d+°)\\s?(\\d+[.]?[\\d]+?')");
			Matcher matcher = pattern.matcher(string);
			if (this.lon.isEmpty() && matcher.find())
			{
				this.lon = matcher.group(1) + " " + matcher.group(2);
			}
		}

		// -78.630752
		{
			Pattern pattern = Pattern.compile("^([\\-]?\\d+\\.\\d+)");
			Matcher matcher = pattern.matcher(string);
			if (this.lon.isEmpty() && matcher.find())
			{
				String val = getFormattedLocationInDegreeLon(Double.parseDouble(matcher.group(1)));
				if (val == null) {
					return new ErrorMessage("Bad decimal degree: " + string);
				}
				this.lon = val;
			}
		}

		if (this.lon.isEmpty()) {
			return new ErrorMessage("Bad lon: " + string);
		}

		return null;
	}

	public String getShortLat() {
		return this.lat.replace(" ", "");
	}

	public String getShortLon() {
		return this.lon.replace(" ", "");
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(project);
		sb.append("/");
		sb.append(title);
		sb.append("/");
		sb.append(version);
		sb.append("/");
		sb.append(author);
		return "ITEM: " + sb.toString();
	}

	public void remove(String string) {
		requiredFields.remove(string);
	}
}
