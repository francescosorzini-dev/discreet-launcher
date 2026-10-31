package com.vincent_falzon.discreetlauncher.core ;

import static org.junit.Assert.assertEquals ;
import org.junit.Test ;

/**
 * Tests of the usage score decay (pure Java part of UsageTracker).
 */
public class UsageTrackerTest
{
	private static final long DAY = 24L * 60 * 60 * 1000 ;

	@Test public void noValue() { assertEquals(0, UsageTracker.decayedScore(null, 0), 0) ; }
	@Test public void noDecayAtOnce() { assertEquals(4, UsageTracker.decayedScore("4.0;1000", 1000), 1e-9) ; }
	@Test public void halvedAfterHalfLife() { assertEquals(2, UsageTracker.decayedScore("4.0;0", 14 * DAY), 1e-9) ; }
	@Test public void quarterAfterTwoHalfLives() { assertEquals(1, UsageTracker.decayedScore("4.0;0", 28 * DAY), 1e-9) ; }
	@Test public void clockGoingBackwards() { assertEquals(4, UsageTracker.decayedScore("4.0;5000", 1000), 1e-9) ; }
	@Test public void corruptedValue() { assertEquals(0, UsageTracker.decayedScore("abc", 1000), 0) ; }
}
