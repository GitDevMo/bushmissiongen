package bushmissiongen.entries;

import junit.framework.TestCase;

public class MetaEntryTest extends TestCase {
	public void testSetLat() {
		MetaEntry me = new MetaEntry();
		me.setLat("12° 17' 49,45\" N");
		assertEquals("N12° 17' 49.45\"", me.lat);

		me = new MetaEntry();
		me.setLat("8°07'34.4\"N");
		assertEquals("N8° 07' 34.4\"", me.lat);

		me = new MetaEntry();
		me.setLat("8° 07' 34.4\" N");
		assertEquals("N8° 07' 34.4\"", me.lat);

		// ----

		me = new MetaEntry();
		me.setLat("N8°07'34.4\"");
		assertEquals("N8° 07' 34.4\"", me.lat);

		me = new MetaEntry();
		me.setLat("N8° 07' 34.4\"");
		assertEquals("N8° 07' 34.4\"", me.lat);

		// ----

		me = new MetaEntry();
		me.setLat("N65°18.25'");
		assertEquals("N65° 18.25'", me.lat);

		me = new MetaEntry();
		me.setLat("N65° 18.25'");
		assertEquals("N65° 18.25'", me.lat);

		// ----

		me = new MetaEntry();
		me.setLat("64.412136");
		assertEquals("N64° 24' 44\"", me.lat);

		me = new MetaEntry();
		me.setLat("-64.412136");
		assertEquals("S64° 24' 44\"", me.lat);
	}

	public void testSetLon() {
		MetaEntry me = new MetaEntry();
		me.setLon("16° 44' 34,86\" E");
		assertEquals("E16° 44' 34.86\"", me.lon);

		me = new MetaEntry();
		me.setLon("98°55'22.0\"E");
		assertEquals("E98° 55' 22.0\"", me.lon);

		me = new MetaEntry();
		me.setLon("98° 55' 22.0\" E");
		assertEquals("E98° 55' 22.0\"", me.lon);

		// ----

		me = new MetaEntry();
		me.setLon("E98°55'22.0\"");
		assertEquals("E98° 55' 22.0\"", me.lon);

		me = new MetaEntry();
		me.setLon("E98° 55' 22.0\"");
		assertEquals("E98° 55' 22.0\"", me.lon);

		// ----

		me = new MetaEntry();
		me.setLon("E17°58.51'");
		assertEquals("E17° 58.51'", me.lon);

		me = new MetaEntry();
		me.setLon("E17° 58.51'");
		assertEquals("E17° 58.51'", me.lon);

		// ----

		me = new MetaEntry();
		me.setLon("78.630752");
		assertEquals("E78° 37' 51\"", me.lon);

		me = new MetaEntry();
		me.setLon("-78.630752");
		assertEquals("W78° 37' 51\"", me.lon);
	}

	public void testGetShortLat() {
		MetaEntry me = new MetaEntry();
		me.setLat("8° 07' 34.4\" N");
		assertEquals("N8°07'34.4\"", me.getShortLat());
	}

	public void testGetShortLon() {
		MetaEntry me = new MetaEntry();
		me.setLon("98° 55' 22.0\" E");
		assertEquals("E98°55'22.0\"", me.getShortLon());
	}

	public void testSetAlt() {
		MetaEntry me = new MetaEntry();
		me.setAlt("+000062.00");
		assertEquals("+000062.00", me.alt);

		me = new MetaEntry();
		me.setAlt("+62.00");
		assertEquals("+000062.00", me.alt);

		me = new MetaEntry();
		me.setAlt("-62");
		assertEquals("-000062.00", me.alt);
	}

	public void testGetShortAlt() {
		MetaEntry me = new MetaEntry();
		me.setAlt("+000062.00");
		assertEquals("+62.00", me.getShortAlt());
	}
}
