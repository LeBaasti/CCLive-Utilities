package net.felix.utilities.Overall.starforged;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LesterCallTest {

	@Test
	void onlyStarforgeableCraftsAtMaxLevelCount() {
		assertTrue(LesterCallCounter.qualifies(80.0, 100));
		assertTrue(LesterCallCounter.qualifies(95.5, 120));
		assertFalse(LesterCallCounter.qualifies(79.9, 100));
		assertFalse(LesterCallCounter.qualifies(90.0, 99));
		assertFalse(LesterCallCounter.qualifies(null, 100));
		assertFalse(LesterCallCounter.qualifies(90.0, null));
	}

	@Test
	void addsAndReachesThreshold() {
		assertEquals(10, LesterCallCounter.add(0, 10));
		assertEquals(5, LesterCallCounter.add(-3, 5));
		assertEquals(Integer.MAX_VALUE, LesterCallCounter.add(Integer.MAX_VALUE, 10));
		assertFalse(LesterCallCounter.reached(999));
		assertTrue(LesterCallCounter.reached(LesterCallCounter.add(995, 10)));
	}

	@Test
	void parsesItemScore() {
		assertEquals(82.5, LesterCallCounter.parseScore("82.5"));
		assertEquals(82.5, LesterCallCounter.parseScore(" 82,5 "));
		assertNull(LesterCallCounter.parseScore("NaN"));
		assertNull(LesterCallCounter.parseScore(""));
		assertNull(LesterCallCounter.parseScore("abc"));
		assertNull(LesterCallCounter.parseScore(null));
	}

	@Test
	void timelineIsConsistent() {
		assertTrue(LesterCallOverlay.isRinging(500f));
		assertFalse(LesterCallOverlay.isRinging(1500f));
		assertFalse(LesterCallOverlay.isRinging(LesterCallOverlay.ACCEPT));
		assertEquals(-1, LesterCallOverlay.lineAt(LesterCallOverlay.ACCEPT));
		assertEquals(0, LesterCallOverlay.lineAt(LesterCallOverlay.ACCEPT + LesterCallOverlay.LINE_STARTS[0]));
		assertEquals(LesterCallOverlay.LINES.length - 1, LesterCallOverlay.lineAt(LesterCallOverlay.CALL_END - 1f));
		assertEquals(-1, LesterCallOverlay.lineAt(LesterCallOverlay.CALL_END));
		assertEquals(LesterCallOverlay.LINES.length, LesterCallOverlay.LINE_STARTS.length);
		for (int i = 1; i < LesterCallOverlay.LINE_STARTS.length; i++) {
			assertTrue(LesterCallOverlay.LINE_STARTS[i] > LesterCallOverlay.LINE_STARTS[i - 1]);
		}
		assertTrue(LesterCallOverlay.ACCEPT + LesterCallOverlay.LINE_STARTS[LesterCallOverlay.LINE_STARTS.length - 1]
				< LesterCallOverlay.CALL_END);
	}

	@Test
	void speechMatchesSubtitles() {
		assertEquals(LesterCallOverlay.LINES.length, LesterCallOverlay.LINE_ENDS.length);
		for (int i = 0; i < LesterCallOverlay.LINE_ENDS.length; i++) {
			assertTrue(LesterCallOverlay.LINE_ENDS[i] > LesterCallOverlay.LINE_STARTS[i]);
			if (i + 1 < LesterCallOverlay.LINE_STARTS.length) {
				assertTrue(LesterCallOverlay.LINE_ENDS[i] <= LesterCallOverlay.LINE_STARTS[i + 1]);
			}
		}
		float previousEnd = 0f;
		for (float[] part : LesterCallOverlay.SPEECH) {
			assertTrue(part[0] >= previousEnd && part[1] > part[0]);
			previousEnd = part[1];
		}
		assertTrue(LesterCallOverlay.ACCEPT + previousEnd < LesterCallOverlay.CALL_END);
		assertTrue(LesterCallOverlay.isTalking(LesterCallOverlay.ACCEPT + 500f));
		assertFalse(LesterCallOverlay.isTalking(LesterCallOverlay.ACCEPT + 1400f));
	}
}
