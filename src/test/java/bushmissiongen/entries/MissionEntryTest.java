package bushmissiongen.entries;

import junit.framework.TestCase;

public class MissionEntryTest extends TestCase {
	public void testSetLatlon() {
		MissionEntry me = new MissionEntry();
		me.setLatlon("12° 17' 49,45\" N 16° 44' 34,86\" E");
		assertEquals("N12° 17' 49.45\",E16° 44' 34.86\"", me.latlon);
		
		me = new MissionEntry();
		me.setLatlon("8°07'34.4\"N 98°55'22.0\"E");
		assertEquals("N8° 07' 34.4\",E98° 55' 22.0\"", me.latlon);
		
		me = new MissionEntry();
		me.setLatlon("8° 07' 34.4\" N 98° 55' 22.0\" E");
		assertEquals("N8° 07' 34.4\",E98° 55' 22.0\"", me.latlon);
		
		// ----
		
		me = new MissionEntry();
		me.setLatlon("N8°07'34.4\",E98°55'22.0\"");
		assertEquals("N8° 07' 34.4\",E98° 55' 22.0\"", me.latlon);
		
		me = new MissionEntry();
		me.setLatlon("N8° 07' 34.4\", E98° 55' 22.0\"");
		assertEquals("N8° 07' 34.4\",E98° 55' 22.0\"", me.latlon);
		
		// ----
		
		me = new MissionEntry();
		me.setLatlon("N65°18.25',E17°58.51'");
		assertEquals("N65° 18.25',E17° 58.51'", me.latlon);
		
		me = new MissionEntry();
		me.setLatlon("N65° 18.25', E17° 58.51'");
		assertEquals("N65° 18.25',E17° 58.51'", me.latlon);
		
		// ----
		
		me = new MissionEntry();
		me.setLatlon("64.412136,-78.630752");
		assertEquals("N64° 24' 44\",W78° 37' 51\"", me.latlon);
		
		me = new MissionEntry();
		me.setLatlon("64.412136, -78.630752");
		assertEquals("N64° 24' 44\",W78° 37' 51\"", me.latlon);
	}

	public void testGetShortLatlon() {
		MissionEntry me = new MissionEntry();
		me.setLatlon("N8° 07' 34.4\", E98° 55' 22.0\"");
		assertEquals("N8°07'34.4\",E98°55'22.0\"", me.getShortLatlon());
	}
	
	public void testSetAlt() {
		MissionEntry me = new MissionEntry();
		me.setAlt("+000062.00");
		assertEquals("+000062.00", me.alt);
		
		me = new MissionEntry();
		me.setAlt("+62.00");
		assertEquals("+000062.00", me.alt);
		
		me = new MissionEntry();
		me.setAlt("-62");
		assertEquals("-000062.00", me.alt);
	}

	public void testGetShortAlt() {
		MissionEntry me = new MissionEntry();
		me.setAlt("+000062.00");
		assertEquals("+62.00", me.getShortAlt());
	}
}
