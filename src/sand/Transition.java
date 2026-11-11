package sand;

/**
 * Defines transition rules for falling sand particle simulation.
 * <p>
 * This class implements the physics rules that govern how particles move and
 * interact in the simulation. It evaluates gravity, collision, and stacking
 * behaviors based on neighbor patterns. The class uses pattern matching to
 * determine how each particle should behave in the next simulation step.
 * </p>
 * <p>
 * The physics simulation includes:
 * <ul>
 *   <li>Gravity - particles fall downward when possible</li>
 *   <li>Collision detection - particles interact with other particles and boundaries</li>
 *   <li>Stacking - particles form piles when they can't fall further</li>
 *   <li>Sliding - particles slide diagonally when direct downward movement is blocked</li>
 *   <li>Hit forces - particles can be moved by user-applied forces</li>
 * </ul>
 * </p>
 * 
 * @see Sand
 * @see Renderer
 */
class Transition {
	/** Hit force pushing particles towards decreasing x. */
	static final int LEFT = -1;
	/** Hit force pushing particles towards increasing x. */
	static final int RIGHT = 1;
	/** Strongest hit force, and the faces of the die that rolls it. */
	static final int MAX_HIT_FORCE = 10;

	private final int cols;
	private final int rows;

	/**
	 * Constructs a new Transition object with the specified dimensions.
	 * 
	 * @param cols number of columns of the simulation grid
	 * @param rows number of rows of the simulation grid
	 */
	public Transition(int cols, int rows) {
		this.cols = cols;
		this.rows = rows;
	}

	/**
	 * Pushes one particle a single cell in the direction of a hit.
	 * <p>
	 * The probability that a particle is affected by the hit is proportional to
	 * the hitForce value (hitForce / {@value #MAX_HIT_FORCE}). The move itself is
	 * a rule like the ones of gravity: the particle slides one cell sideways when
	 * that side is free and is copied to {@code nextCells} unchanged when it
	 * cannot move or when the dice say the hit misses it.
	 * </p>
	 * 
	 * @param x            the x coordinate of the particle
	 * @param y            the y coordinate of the particle
	 * @param cells        the current cell data
	 * @param nextCells    the next cell data (to be updated)
	 * @param hitForce     the strength of the hit (1 to {@value #MAX_HIT_FORCE})
	 * @param hitDirection the direction of the hit (1 = right, -1 = left)
	 */
	public void pushParticle(int x, int y, int[][] cells, int[][] nextCells, int hitForce, int hitDirection) {
		// Roll dice to determine if this particle is affected by the hit force
		// Higher hitForce = higher probability of being affected
		// (hitForce / MAX_HIT_FORCE chance)
		int dice = (int) (Math.random() * MAX_HIT_FORCE);

		// Check if particle should be affected by the hit force
		if (dice < hitForce) {
			Step step = hitDirection == RIGHT ? Pattern.PUSH_RIGHT_STEP : Pattern.PUSH_LEFT_STEP;
			if (matches(step.match, x, y, cells)) {
				applyPattern(step, x, y, cells, nextCells);
				return;
			}
		}
		// If particle wasn't moved by hit force, keep it in the same position,
		// unless a neighbour has already been pushed into this cell
		if (nextCells[y][x] == 0)
			nextCells[y][x] = cells[y][x];
	}

	/**
	 * Advances one particle according to gravity, collisions and stacking.
	 * <p>
	 * The step rules are tried in order and the first neighbourhood that matches
	 * is applied, so the table reads from the most open movement to the blocked
	 * one:
	 * <ul>
	 *   <li>Particles fall due to gravity</li>
	 *   <li>Slide to a free diagonal when the direct fall is blocked</li>
	 *   <li>Stack when there is no free cell left</li>
	 * </ul>
	 * </p>
	 * 
	 * @param x         the x coordinate of the particle
	 * @param y         the y coordinate of the particle
	 * @param cells     the current cell data
	 * @param nextCells the next cell data (to be updated)
	 */
	public void stepParticle(int x, int y, int[][] cells, int[][] nextCells) {
		for (Step step : Pattern.STEPS) {
			if (matches(step.match, x, y, cells)) {
				applyPattern(step, x, y, cells, nextCells);
				return;
			}
		}
		// Every neighbourhood is covered by one of the steps above, so this is
		// only a safety net: the particle is kept instead of lost
		nextCells[y][x] = cells[y][x];
	}

	/**
	 * Writes the result of a rule into the next generation.
	 * <p>
	 * The result is a small drawing of what the 3x3 neighbourhood becomes:
	 * <ul>
	 *   <li>{@link Pattern#EMPTY}: the cell is emptied</li>
	 *   <li>{@link Pattern#SOLID}: the particle is written there</li>
	 *   <li>{@link Pattern#SHARED}: the particle is written in one of these cells,
	 *       each one with one chance in as many as there are, so of two free
	 *       diagonals each one is used half of the time. Only one of them receives
	 *       the particle and the rest stay empty; when none of them wins the dice
	 *       the particle still goes to the last one</li>
	 *   <li>{@link Pattern#DONT_CARE}: nothing is written</li>
	 * </ul>
	 * The {@code ?} cells need no copy: a neighbour holding a particle is a
	 * particle of its own that moves in its own step, and copying it here would
	 * leave a second copy in the cell it just left.
	 * </p>
	 *
	 * @param step      the rule being applied, its result is what gets written
	 * @param x         the x coordinate of the particle
	 * @param y         the y coordinate of the particle
	 * @param cells     the current cell data
	 * @param nextCells the next cell data (to be updated)
	 */
	private void applyPattern(Step step, int x, int y, int[][] cells, int[][] nextCells) {
		String[] result = step.result;
		int particle = cells[y][x];
		// Without a destination the particle stays where it was
		int destinationX = x;
		int destinationY = y;
		int lastSharedX = -1; // last "x" of the result, the one that catches a miss
		int lastSharedY = -1;
		boolean hit = false;

		for (int py = 0; py < result.length; py++) {
			for (int px = 0; px < result[py].length(); px++) {
				char glyph = result[py].charAt(px);
				int tx = x + px - 1;
				int ty = y + py - 1;
				if (!inside(tx, ty))
					continue;
				if (glyph == Pattern.EMPTY) {
					nextCells[ty][tx] = 0;
				} else if (glyph == Pattern.SOLID) {
					destinationX = tx;
					destinationY = ty;
				} else if (glyph == Pattern.SHARED) {
					lastSharedX = tx;
					lastSharedY = ty;
					// Die with one face per "x": 0 wins. Once one wins the others are
					// not rolled any more
					if (!hit && (int) (Math.random() * step.shared) == 0) {
						destinationX = tx;
						destinationY = ty;
						hit = true;
					}
				}
			}
		}

		// Every "x" missed the dice: the last one takes the particle anyway
		if (!hit && lastSharedX >= 0) {
			destinationX = lastSharedX;
			destinationY = lastSharedY;
		}

		// The particle is written after the cells are emptied, and when another
		// particle took the destination this frame it stays where it was, so none
		// of them is lost
		if (!isFree(destinationX, destinationY, nextCells)) {
			destinationX = x;
			destinationY = y;
		}
		nextCells[destinationY][destinationX] = particle;
	}

	/**
	 * Checks whether a pattern matches the neighbourhood of a cell.
	 * <p>
	 * The pattern is a 3x3 matrix: row 0 is the row above the cell, row 1 the row
	 * of the cell and row 2 the row below it. Values that do not match make the
	 * method return immediately.
	 * </p>
	 *
	 * @param pattern the 3x3 pattern to match, three rows of three characters
	 * @param x       the x coordinate of the cell to check
	 * @param y       the y coordinate of the cell to check
	 * @param cells   the cell data to check against
	 * @return true if the pattern matches, false otherwise
	 */
	private boolean matches(String[] pattern, int x, int y, int[][] cells) {
		for (int py = 0; py < pattern.length; py++) {
			for (int px = 0; px < pattern[py].length(); px++) {
				char wanted = pattern[py].charAt(px);
				if (wanted == Pattern.DONT_CARE)
					continue; // this neighbour does not matter
				if (wanted != occupancy(x + px - 1, y + py - 1, cells))
					return false;
			}
		}
		return true;
	}

	/**
	 * What a position looks like to the pattern matcher.
	 * <p>
	 * Lateral and bottom borders count as solid, so particles stay inside the
	 * canvas, while everything above the grid counts as empty, so particles can
	 * fall in from the top.
	 * </p>
	 *
	 * @param x     the x coordinate to inspect, may be outside the grid
	 * @param y     the y coordinate to inspect, may be outside the grid
	 * @param cells the cell data
	 * @return {@link Pattern#SOLID} if the position holds a particle or is a
	 *         border, {@link Pattern#EMPTY} otherwise
	 */
	private char occupancy(int x, int y, int[][] cells) {
		if (x < 0 || x >= cols || y >= rows)
			return Pattern.SOLID; // lateral and bottom borders are solid
		if (y < 0)
			return Pattern.EMPTY; // above the grid counts as free space
		return cells[y][x] == 0 ? Pattern.EMPTY : Pattern.SOLID;
	}

	/**
	 * Checks whether a position belongs to the simulation grid.
	 *
	 * @param x the x coordinate to check, may be outside the grid
	 * @param y the y coordinate to check, may be outside the grid
	 * @return true if the position is inside the grid
	 */
	private boolean inside(int x, int y) {
		return x >= 0 && x < cols && y >= 0 && y < rows;
	}

	/**
	 * Checks whether a cell can still receive a particle in the next generation.
	 *
	 * @param x         the x coordinate to check, may be outside the grid
	 * @param y         the y coordinate to check, may be outside the grid
	 * @param nextCells the next cell data
	 * @return true if the position is inside the grid and still empty
	 */
	private boolean isFree(int x, int y, int[][] nextCells) {
		return inside(x, y) && nextCells[y][x] == 0;
	}

	/**
	 * A step rule: the 3x3 neighbourhood that triggers it and the 3x3 result that
	 * is written into the next generation when it does.
	 * <p>
	 * Both patterns use the glyphs of {@link Pattern}, so a rule reads as two
	 * stacked drawings: how the neighbourhood looks now and how it looks after
	 * the step.
	 * </p>
	 */
	private static class Step {
		final String[] match;
		final String[] result;
		/** Number of {@code x} of the result: they share the particle between them. */
		final int shared;

		Step(String[] match, String[] result) {
			this.match = match;
			this.result = result;
			this.shared = countShared(result);
		}

		/**
		 * Number of {@code x} cells of a result.
		 * <p>
		 * They share the particle between them, so this is both the faces of the
		 * die rolled for each one and what makes their chances add up to one. It
		 * only depends on the rule, so it is counted once here instead of on every
		 * step of every particle.
		 * </p>
		 *
		 * @param result the 3x3 result of the rule
		 * @return the number of {@code x} cells, 0 when there is none
		 */
		private static int countShared(String[] result) {
			int shared = 0;
			for (String row : result)
				for (int px = 0; px < row.length(); px++)
					if (row.charAt(px) == Pattern.SHARED)
						shared++;
			return shared;
		}
	}

	/**
	 * Fixed 3x3 neighbourhoods used to decide how a particle moves.
	 * <p>
	 * Each pattern is written as three rows of three characters, so the drawing
	 * below is the pattern itself:
	 * <ul>
	 *   <li>{@link #DONT_CARE}: the neighbour can be anything</li>
	 *   <li>{@link #SOLID}: particle present (borders count as solid too)</li>
	 *   <li>{@link #EMPTY}: free space</li>
	 *   <li>{@link #SHARED}: like {@link #SOLID}, but it shares the probability
	 *       with the other {@code x} cells of the same result</li>
	 * </ul>
	 * In a match the center character (row 1, column 1) is the particle being
	 * evaluated, so every match has a {@link #SOLID} there. In a result that same
	 * position is what the cell of the particle becomes.
	 * </p>
	 */
	private static class Pattern {
		private static final char DONT_CARE = '?';
		private static final char SOLID = 'X';
		private static final char EMPTY = '_';
		private static final char SHARED = 'x';

		/**
		 * Free space on the right of the particle: a hit pushes it one cell right.
		 */
		private static final Step PUSH_RIGHT_STEP = new Step(
				new String[] {
						"???",
						"?X_",
						"???" },
				new String[] {
						"???",
						"?_X",
						"???" });

		/**
		 * Free space on the left of the particle: a hit pushes it one cell left.
		 */
		private static final Step PUSH_LEFT_STEP = new Step(
				new String[] {
						"???",
						"_X?",
						"???" },
				new String[] {
						"???",
						"X_?",
						"???" });

		/** Free fall: the space below the particle is empty, so it drops one row. */
		private static final Step FREE_FALL_STEP = new Step(
				new String[] {
						"???",
						"?X?",
						"?_?" },
				new String[] {
						"???",
						"?_?",
						"?X?" });

		/** Something below, but both diagonals are free: slides to a random side. */
		private static final Step SLIDE_BOTH_STEP = new Step(
				new String[] {
						"???",
						"?X?",
						"_X_" },
				new String[] {
						"???",
						"?_?",
						"x?x" });

		/** Something below and free space only at the left-down diagonal. */
		private static final Step SLIDE_LEFT_STEP = new Step(
				new String[] {
						"???",
						"?X?",
						"_XX" },
				new String[] {
						"???",
						"?_?",
						"X??" });

		/** Something below and free space only at the right-down diagonal. */
		private static final Step SLIDE_RIGHT_STEP = new Step(
				new String[] {
						"???",
						"?X?",
						"XX_" },
				new String[] {
						"???",
						"?_?",
						"??X" });

		/**
		 * Something below and no free diagonal (the bottom border counts as solid
		 * too): the particle stacks on top of the pile.
		 */
		private static final Step STAY_STEP = new Step(
				new String[] {
						"???",
						"?X?",
						"XXX" },
				new String[] {
						"???",
						"?X?",
						"???" });

		/**
		 * Step rules in the order they are tried: the first neighbourhood that
		 * matches decides the move, so the table goes from the most open movement
		 * (fall) to the blocked one (stack).
		 */
		private static final Step[] STEPS = {
				FREE_FALL_STEP,
				SLIDE_BOTH_STEP,
				SLIDE_LEFT_STEP,
				SLIDE_RIGHT_STEP,
				STAY_STEP };
	}

}
