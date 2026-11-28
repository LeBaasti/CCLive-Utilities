package net.felix.utilities.Overall.starforged;

/**
 * Pure rules for the Lester phone call: which crafts count and when the call is due.
 * Only crafts that could become "Sternengeschmiedet" count, i.e. an item score of at least
 * {@link #MIN_ITEM_SCORE} (see {@code ForgingConditionCalculator}) at smith level {@link #REQUIRED_SMITH_LEVEL}.
 */
final class LesterCallCounter {

	/** Counted crafts without "Sternengeschmiedet" that make Lester call. */
	static final int THRESHOLD = 1000;
	/** Lowest item score at which "Sternengeschmiedet" is possible. */
	static final double MIN_ITEM_SCORE = 80.0;
	static final int REQUIRED_SMITH_LEVEL = 100;

	private LesterCallCounter() {
	}

	/** True if a craft of an item with this score at this smith level counts; unknown values never count. */
	static boolean qualifies(Double itemScore, Integer smithLevel) {
		return itemScore != null && smithLevel != null
				&& itemScore >= MIN_ITEM_SCORE && smithLevel >= REQUIRED_SMITH_LEVEL;
	}

	/** New counter value after crafts; never negative, never overflows. */
	static int add(int current, int crafts) {
		long sum = (long) Math.max(0, current) + Math.max(0, crafts);
		return (int) Math.min(Integer.MAX_VALUE, sum);
	}

	static boolean reached(int count) {
		return count >= THRESHOLD;
	}

	/** Parses an item score such as "82.5", "82,5" or "NaN"; null if missing or not a number. */
	static Double parseScore(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			double value = Double.parseDouble(raw.trim().replace(',', '.'));
			return Double.isNaN(value) ? null : value;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
