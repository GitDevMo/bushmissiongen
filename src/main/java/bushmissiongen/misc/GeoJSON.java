package bushmissiongen.misc;

import bushmissiongen.entries.MissionEntry;

public class GeoJSON {
	private StringBuffer mBuffer = new StringBuffer();
	private int mCount;
	
	public GeoJSON() {}
	
	public void reset() {
		mCount = 0;
		mBuffer = new StringBuffer();

		mBuffer.append("{").append(System.lineSeparator());
		mBuffer.append("\"type\": \"FeatureCollection\",").append(System.lineSeparator());
		mBuffer.append("\"features\": [").append(System.lineSeparator());
	}

	@Override
	public String toString() {
		return mBuffer.toString();
	}

	public void finish() {
		mBuffer.append("]").append(System.lineSeparator());
		mBuffer.append("}").append(System.lineSeparator());
	}
	
	public void appendLine(String fromLatLon, String toLatLon) {
		mBuffer.append(mCount==0 ? "{" : ",{").append(System.lineSeparator());
		mBuffer.append("\"type\": \"Feature\",").append(System.lineSeparator());
		mBuffer.append("\"properties\": {},").append(System.lineSeparator());
		mBuffer.append("\"geometry\": {").append(System.lineSeparator());
		mBuffer.append("\"type\": \"LineString\",").append(System.lineSeparator());
		mBuffer.append("\"coordinates\": [").append(System.lineSeparator());
		double[] coord1 = MissionEntry.convertCoordinate(fromLatLon);
		double[] coord2 = MissionEntry.convertCoordinate(toLatLon);
		mBuffer.append("[" + coord1[0] + "," + coord1[1] + "],").append(System.lineSeparator());
		mBuffer.append("[" + coord2[0] + "," + coord2[1] + "]").append(System.lineSeparator());
		mBuffer.append("]").append(System.lineSeparator());
		mBuffer.append("}").append(System.lineSeparator());
		mBuffer.append("}").append(System.lineSeparator());
		mCount++;
	}
	
	public void appendPoint(String latLon, String color) {
		mBuffer.append(mCount==0 ? "{" : ",{").append(System.lineSeparator());
		mBuffer.append("\"type\": \"Feature\",").append(System.lineSeparator());
		mBuffer.append("\"properties\": {").append(System.lineSeparator());
		mBuffer.append("\"marker-color\": \"" + color + "\",").append(System.lineSeparator());
		mBuffer.append("\"marker-size\": \"medium\",").append(System.lineSeparator());
		mBuffer.append("\"marker-symbol\": \"\"").append(System.lineSeparator());
		mBuffer.append("},").append(System.lineSeparator());
		mBuffer.append("\"geometry\": {").append(System.lineSeparator());
		mBuffer.append("\"type\": \"Point\",").append(System.lineSeparator());
		mBuffer.append("\"coordinates\": [").append(System.lineSeparator());
		double[] coord = MissionEntry.convertCoordinate(latLon);
		mBuffer.append(coord[0] + "," + coord[1]).append(System.lineSeparator());
		mBuffer.append("]").append(System.lineSeparator());
		mBuffer.append("}").append(System.lineSeparator());
		mBuffer.append("}").append(System.lineSeparator());
		mCount++;
	}
	
	public void appendPolygon(String latLon, String width, String height, String heading, String stroke, String fill) {
		mBuffer.append(mCount==0 ? "{" : ",{").append(System.lineSeparator());
		mBuffer.append("\"type\": \"Feature\",").append(System.lineSeparator());
		mBuffer.append("\"properties\": {").append(System.lineSeparator());
		mBuffer.append("\"stroke\": \"" + stroke + "\",").append(System.lineSeparator());
		mBuffer.append("\"stroke-width\": 2,").append(System.lineSeparator());
		mBuffer.append("\"stroke-opacity\": 1,").append(System.lineSeparator());
		mBuffer.append("\"fill\": \"" + fill + "\",").append(System.lineSeparator());
		mBuffer.append("\"fill-opacity\": 0.5").append(System.lineSeparator());
		mBuffer.append("},").append(System.lineSeparator());
		mBuffer.append("\"geometry\": {").append(System.lineSeparator());
		mBuffer.append("\"type\": \"Polygon\",").append(System.lineSeparator());
		mBuffer.append("\"coordinates\": [").append(System.lineSeparator());
		mBuffer.append("[").append(System.lineSeparator());
		double[] coord = MissionEntry.convertCoordinate(latLon);
		MyGeoPoint[] box = MissionEntry.getBoundingBox(coord, Double.parseDouble(width), Double.parseDouble(height), Double.parseDouble(heading));
		mBuffer.append("[" + box[0].longitude + "," + box[0].latitude + "],").append(System.lineSeparator());
		mBuffer.append("[" + box[1].longitude + "," + box[1].latitude + "],").append(System.lineSeparator());
		mBuffer.append("[" + box[2].longitude + "," + box[2].latitude + "],").append(System.lineSeparator());
		mBuffer.append("[" + box[3].longitude + "," + box[3].latitude + "]").append(System.lineSeparator());
		mBuffer.append("]").append(System.lineSeparator());
		mBuffer.append("]").append(System.lineSeparator());
		mBuffer.append("}").append(System.lineSeparator());
		mBuffer.append("}").append(System.lineSeparator());
		mCount++;
	}
}
