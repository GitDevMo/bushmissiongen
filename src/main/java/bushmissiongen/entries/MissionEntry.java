package bushmissiongen.entries;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bushmissiongen.messages.ErrorMessage;
import bushmissiongen.messages.Message;
import bushmissiongen.misc.Localization;
import bushmissiongen.misc.MyGeoPoint;

public class MissionEntry extends GenericEntry {
	public final static String TYPE_AIRPORT = "Airport";
	public final static String TYPE_USER = "User";

	public String region = "";
	public String id = "";
	public String runway = "";
	public String name = "";
	public WpType type = null;
	public String latlon = "";
	public String wpInfo = "";
	public String legText = "";
	public String subLegText = "";

	public String navlogImage = "";
	public String navlogImageSize = "";

	public String uniqueId = "";

	public List<Localization> localizations = new ArrayList<Localization>();

	public String nameID = "?";
	public String legTextID = "?";
	public String subLegTextID = "?";

	public enum WpType {
		AIRPORT,
		USER
	};

	// Normal constructor
	public MissionEntry() {		
	}

	// Clone constructor
	public MissionEntry(MissionEntry other) {
		this.region = other.region;
		this.id = other.id;
		this.runway = other.runway;
		this.name = other.name;
		this.type = other.type;
		this.latlon = other.latlon;
		this.alt = other.alt;
		this.wpInfo = other.wpInfo;
		this.legText = other.legText;
		this.subLegText = other.subLegText;
	}

	private String getFormattedLocationInDegree(double latitude, double longitude) {
		try {
			int latSeconds = (int) Math.round(latitude * 3600);
			int latDegrees = latSeconds / 3600;
			latSeconds = Math.abs(latSeconds % 3600);
			int latMinutes = latSeconds / 60;
			latSeconds %= 60;

			int longSeconds = (int) Math.round(longitude * 3600);
			int longDegrees = longSeconds / 3600;
			longSeconds = Math.abs(longSeconds % 3600);
			int longMinutes = longSeconds / 60;
			longSeconds %= 60;
			String latDegree = latDegrees >= 0 ? "N" : "S";
			String lonDegree = longDegrees >= 0 ? "E" : "W";

			return latDegree + Math.abs(latDegrees) + "° " + latMinutes + "' " + latSeconds
					+ "\"" +  ","+ lonDegree + Math.abs(longDegrees) + "° " + longMinutes
					+ "' " + longSeconds + "\"";
		} catch (Exception e) {
			return null;
		}
	}

	public static MyGeoPoint rotatePoint(MyGeoPoint point, MyGeoPoint origion, double degree) {
		double x = origion.longitude + (Math.cos(Math.toRadians(degree)) * (point.longitude - origion.longitude) - Math.sin(Math.toRadians(degree))  * (point.latitude - origion.latitude) / Math.abs(Math.cos(Math.toRadians(origion.latitude))));
		double y = origion.latitude  + (Math.sin(Math.toRadians(degree)) * (point.longitude - origion.longitude) * Math.abs(Math.cos(Math.toRadians(origion.latitude))) + Math.cos(Math.toRadians(degree))   * (point.latitude - origion.latitude));
		return new MyGeoPoint(x, y);
	}

	public static MyGeoPoint[] getBoundingBox(double[] coordinate, double dn, double de, double heading) {
		double R1=6378137.0;
		//	    double R2=6371010.0;
		double R = R1;

		dn = dn/2.0;
		de = de/2.0;

		double lat0 = Math.cos(Math.PI / 180.0 * coordinate[0]);
		double minLat = coordinate[0] - (180/Math.PI)*(de/R)/Math.cos(lat0);
		double minLong = coordinate[1] - (180/Math.PI)*(dn/R);
		double maxLat = coordinate[0] + (180/Math.PI)*(de/R)/Math.cos(lat0);
		double maxLong = coordinate[1] + (180/Math.PI)*(dn/R);

		MyGeoPoint p1 = rotatePoint(new MyGeoPoint(minLat, minLong), new MyGeoPoint(coordinate[0], coordinate[1]), heading);
		MyGeoPoint p2 = rotatePoint(new MyGeoPoint(minLat, maxLong), new MyGeoPoint(coordinate[0], coordinate[1]), heading);
		MyGeoPoint p3 = rotatePoint(new MyGeoPoint(maxLat, maxLong), new MyGeoPoint(coordinate[0], coordinate[1]), heading);
		MyGeoPoint p4 = rotatePoint(new MyGeoPoint(maxLat, minLong), new MyGeoPoint(coordinate[0], coordinate[1]), heading);

		return new MyGeoPoint[] {p1, p2, p3, p4};
	}

	public static double[] convertCoordinate(String string) {
		Pattern pattern = Pattern.compile("^([NS])(\\d+)°\\s?(\\d+)'\\s?(\\d+|\\d+[.]\\d+)\",\\s?([WE])(\\d+)°\\s?(\\d+)'\\s?(\\d+|\\d+[.]\\d+)\"");
		Matcher matcher = pattern.matcher(string);
		if (matcher.find())
		{
			String latDegree = matcher.group(1);
			double latDegrees = Integer.parseInt(matcher.group(2));
			double latMinutes = Double.parseDouble(matcher.group(3));
			double latSeconds = Double.parseDouble(matcher.group(4));

			String longDegree = matcher.group(5);
			double longDegrees = Integer.parseInt(matcher.group(6));
			double longMinutes = Double.parseDouble(matcher.group(7));
			double longSeconds = Double.parseDouble(matcher.group(8));

			latDegrees += ((latMinutes * 60)+latSeconds) / (60*60);
			longDegrees += ((longMinutes * 60)+longSeconds) / (60*60);

			latDegrees = latDegree.equals("N") ? latDegrees : -1 * latDegrees;
			longDegrees = longDegree.equals("E") ? longDegrees : -1 * longDegrees;

			return new double[] {longDegrees, latDegrees};
		} else {
			// N65° 18.25',E17° 58.51'
			{
				pattern = Pattern.compile("^([NS])(\\d+)°\\s?(\\d+[.]?[\\d]+?)',\\s?([WE])(\\d+)°\\s?(\\d+[.]?[\\d]+?)'");
				matcher = pattern.matcher(string);
				if (matcher.find())
				{
					String latDegree = matcher.group(1);
					double latDegrees = Integer.parseInt(matcher.group(2));
					double latMinutes = Double.parseDouble(matcher.group(3));

					String longDegree = matcher.group(4);
					double longDegrees = Integer.parseInt(matcher.group(5));
					double longMinutes = Double.parseDouble(matcher.group(6));

					latDegrees += ((latMinutes * 60)+0) / (60*60);
					longDegrees += ((longMinutes * 60)+0) / (60*60);

					latDegrees = latDegree.equals("N") ? latDegrees : -1 * latDegrees;
					longDegrees = longDegree.equals("E") ? longDegrees : -1 * longDegrees;

					return new double[] {longDegrees, latDegrees};
				}
			}
		}

		return null;		
	}

	public Message setLatlon(String string) {
		// Check format!

		// 12° 17' 49,45" N 16° 44' 34,86" E  (Little Navmap)
		{
			Pattern pattern = Pattern.compile("^(\\d+°)\\s?(\\d+')\\s?(\\d+[,]?[\\d]+?\\\")\\s?([NS])\\s(\\d+°)\\s?(\\d+')\\s?(\\d+[,]?[\\d]+?\\\")\\s?([WE])");
			Matcher matcher = pattern.matcher(string);
			if (this.latlon.isEmpty() && matcher.find())
			{
				this.latlon = matcher.group(4) + matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3).replace(",", ".") + "," + matcher.group(8) + matcher.group(5) + " " + matcher.group(6) + " " + matcher.group(7).replace(",", ".");
			}
		}

		// 8°07'34.4"N 98°55'22.0"E --> 
		{
			Pattern pattern = Pattern.compile("^(\\d+°)\\s?(\\d+')\\s?(\\d+[.]?[\\d]+?\\\")\\s?([NS])\\s(\\d+°)\\s?(\\d+')\\s?(\\d+[.]?[\\d]+?\\\")\\s?([WE])");
			Matcher matcher = pattern.matcher(string);
			if (this.latlon.isEmpty() && matcher.find())
			{
				this.latlon = matcher.group(4) + matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3) + "," + matcher.group(8) + matcher.group(5) + " " + matcher.group(6) + " " + matcher.group(7);
			}
		}

		// N8° 07' 34.4",E98° 55' 22.0"
		{
			Pattern pattern = Pattern.compile("^([NS]\\d+°)\\s?(\\d+')\\s?(\\d+[.]?[\\d]+?\"),\\s?([WE]\\d+°)\\s?(\\d+')\\s?(\\d+[.]?[\\d]+?\")");
			Matcher matcher = pattern.matcher(string);
			if (this.latlon.isEmpty() && matcher.find())
			{
				this.latlon = matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3) + "," + matcher.group(4) + " " + matcher.group(5) + " " + matcher.group(6);
			}
		}

		// N65° 18.25',E17° 58.51'
		{
			Pattern pattern = Pattern.compile("^([NS]\\d+°)\\s?(\\d+[.]?[\\d]+?'),\\s?([WE]\\d+°)\\s?(\\d+[.]?[\\d]+?')");
			Matcher matcher = pattern.matcher(string);
			if (this.latlon.isEmpty() && matcher.find())
			{
				this.latlon = matcher.group(1) + " " + matcher.group(2) + "," + matcher.group(3) + " " + matcher.group(4);
			}
		}

		// 64.412136, -78.630752
		{
			Pattern pattern = Pattern.compile("^([\\-]?\\d+\\.\\d+),\\s?([\\-]?\\d+\\.\\d+)");
			Matcher matcher = pattern.matcher(string);
			if (this.latlon.isEmpty() && matcher.find())
			{
				String val = getFormattedLocationInDegree(Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2)));
				if (val == null) {
					return new ErrorMessage("Bad decimal degree: " + string);
				}
				this.latlon = val;
			}
		}

		if (this.latlon.isEmpty()) {
			return new ErrorMessage("Bad latlong: " + string);
		}

		return null;
	}

	public String getShortLatlon() {
		return this.latlon.replace(" ", "");
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(id);
		sb.append("/");
		sb.append(name);
		sb.append("/");
		sb.append(type.toString());
		sb.append("/");
		sb.append(latlon);
		return "ITEM: " + sb.toString();
	}


}
