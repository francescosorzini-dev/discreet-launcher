package com.vincent_falzon.discreetlauncher ;

import static org.junit.Assert.assertEquals ;
import java.util.ArrayList ;
import java.util.Arrays ;
import java.util.Collections ;
import java.util.List ;
import org.junit.Before ;
import org.junit.Test ;

/**
 * Tests of the search ranking (pure Java, runs on the build machine).
 */
public class SearchMatcherTest
{
	private List<SearchMatcher.Entry<String>> entries ;


	/**
	 * Build a sample list of applications: name, folder (or null), usage score.
	 */
	@Before
	public void setUp()
	{
		Object[][] apps = {
			{"Calendario", null, 0d}, {"Chrome", null, 5d}, {"Google Maps", null, 0d}, {"Gmail", "Posta", 9d},
			{"Intesa Sanpaolo", "Banca", 0d}, {"Météo", null, 0d}, {"Romeo", null, 0d}, {"WhatsApp", "Social", 20d},
			{"Firefox", null, 1d}, {"Poste Italiane", "Banca", 0d}
		} ;
		entries = new ArrayList<>() ;
		for(int i = 0 ; i < apps.length ; i++)
		{
			String folder = (String)apps[i][1] ;
			entries.add(new SearchMatcher.Entry<>((String)apps[i][0], (String)apps[i][0],
					(folder == null) ? null : Collections.singletonList(folder), (Double)apps[i][2], i)) ;
		}
	}


	private void check(String search, String... expected)
	{
		assertEquals("search \"" + search + "\"", Arrays.asList(expected), SearchMatcher.rank(entries, search)) ;
	}


	@Test public void prefix() { check("chr", "Chrome") ; }
	@Test public void substringAfterPrefix() { check("rome", "Romeo", "Chrome") ; }
	@Test public void accentsAndCase() { check("METEO", "Météo") ; }
	@Test public void wordPrefix() { check("maps", "Google Maps") ; }
	@Test public void prefixBeatsInitials() { check("gm", "Gmail", "Google Maps") ; }
	@Test public void camelCaseInitials() { check("wa", "WhatsApp") ; }
	@Test public void camelCaseWord() { check("app", "WhatsApp") ; }
	@Test public void folderLabel() { check("banca", "Intesa Sanpaolo", "Poste Italiane") ; }
	@Test public void folderAfterName() { check("post", "Poste Italiane", "Gmail") ; }
	@Test public void folderNotMatchedInsideWords() { check("ocial") ; }
	@Test public void mostUsedFirstInSameLevel() { check("g", "Gmail", "Google Maps") ; }
	@Test public void levelsThenUsage() { check("i", "Intesa Sanpaolo", "Poste Italiane", "Gmail", "Firefox", "Calendario") ; }
	@Test public void spacesAndCase() { check("  GOOGLE m ", "Google Maps") ; }
	@Test public void noMatch() { check("xyz") ; }


	@Test
	public void withoutSearchMostUsedFirst()
	{
		assertEquals(Arrays.asList("WhatsApp", "Gmail", "Chrome", "Calendario", "Google Maps", "Intesa Sanpaolo", "Météo", "Romeo", "Firefox", "Poste Italiane"),
				SearchMatcher.mostUsedFirst(entries, 3)) ;
	}


	@Test
	public void withoutSearchNoUsageKeepsOrder()
	{
		List<SearchMatcher.Entry<String>> unused = new ArrayList<>() ;
		unused.add(new SearchMatcher.Entry<>("B", "B", null, 0, 0)) ;
		unused.add(new SearchMatcher.Entry<>("A", "A", null, 0, 1)) ;
		assertEquals(Arrays.asList("B", "A"), SearchMatcher.mostUsedFirst(unused, 8)) ;
	}
}
