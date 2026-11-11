package principal;

/**
 * Defines the rules of the materials simulation.
 * <p>
 * Every rule is a match and a result: the neighbourhood that triggers it and
 * what that neighbourhood becomes, written with the glyphs of {@link Pattern}.
 * The rules are tried in order and the first match wins, so the table is read
 * as a priority list: the life transitions first, then the behaviour of each
 * material, and at the end the generic gravity that every particle obeys.
 * </p>
 * <p>
 * The simulation includes:
 * <ul>
 *   <li>Gravity, sliding and stacking for any particle</li>
 *   <li>Materials, each one with its own band of palette values</li>
 *   <li>Life, that the {@code V}, {@code W}, {@code r} and {@code l} glyphs walk
 *       until the rule that transforms the particle fires</li>
 *   <li>Creation, by copying a particle with {@code o} or by writing a material
 *       where there was nothing</li>
 *   <li>The slow sink of the sand: a drop of water with a grain on top of it
 *       holds it for a moment, and then the two swap places</li>
 *   <li>Hit forces, that move particles sideways</li>
 * </ul>
 * </p>
 *
 * @see MaterialSimulator
 * @see WorldRenderer
 * @see Material
 */
class MaterialRules {
	/** Hit force pushing particles towards decreasing x. */
	static final int LEFT = -1;
	/** Hit force pushing particles towards increasing x. */
	static final int RIGHT = 1;
	/** Strongest hit force, and the faces of the die that rolls it. */
	static final int MAX_HIT_FORCE = 10;

	private final int cols;
	private final int rows;

	/**
	 * Constructs a new MaterialRules object with the specified dimensions.
	 * 
	 * @param cols number of columns of the simulation grid
	 * @param rows number of rows of the simulation grid
	 */
	public MaterialRules(int cols, int rows) {
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
			if (matches(step, x, y, cells)) {
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
			if (matches(step, x, y, cells)) {
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
	 * The result is a small drawing of what the neighbourhood becomes, and each
	 * glyph says what happens in its cell:
	 * <ul>
	 *   <li>{@link Material#EMPTY}: the cell is emptied</li>
	 *   <li>{@link Material#PARTICLE}: the particle is written there, with its
	 *       value, so it moves</li>
	 *   <li>{@link Pattern#SHARED}: the particle is written in one of these cells,
	 *       each one with one chance in as many as there are, so of two free
	 *       diagonals each one is used half of the time. Only one of them receives
	 *       the particle and the rest stay empty; when none of them wins the dice
	 *       the particle still goes to the last one</li>
	 *   <li>{@link Pattern#REPEATED}: the particle is copied in each of these
	 *       cells with one chance in as many as there are, and each one rolls its
	 *       own dice, so several of them can win: this is how a material is
	 *       created and not only moved</li>
	 *   <li>{@link Pattern#WRAPS}, {@link Pattern#CLAMPS}, {@link Pattern#BURNS}
	 *       and {@link Pattern#FEEDS}: like a particle, but written with its value
	 *       one step older, one step older without wrapping, half of the life it
	 *       has left older, or half of the life it has spent younger</li>
	 *   <li>a material glyph: a new particle of that material is created there</li>
	 *   <li>{@link Material#ANY}: nothing is written</li>
	 * </ul>
	 * The {@code ?} cells need no copy: a neighbour holding a particle is a
	 * particle of its own that moves in its own step, and copying it here would
	 * leave a second copy in the cell it just left.
	 * </p>
	 * <p>
	 * The particle itself is written once, when the pass is over, in the cell that
	 * claimed it. When no glyph claimed it and its own cell was left empty, it
	 * stays where it was; when the rule wrote something else in its cell, the
	 * particle is consumed, which is how a material becomes another one.
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

		// Where the particle ends up, and with which value. Only the glyphs that
		// carry it claim a destination; without a claim it stays where it was,
		// unless the rule wrote something else in its own cell
		boolean claimed = false;
		int destinationX = x;
		int destinationY = y;
		int destinationValue = particle;
		boolean originWritten = false;

		int lastSharedX = -1; // last "x" of the result, the one that catches a miss
		int lastSharedY = -1;
		boolean hit = false;

		for (int py = 0; py < result.length; py++) {
			for (int px = 0; px < result[py].length(); px++) {
				char glyph = result[py].charAt(px);
				int tx = x + px - step.anchorX;
				int ty = y + py - step.anchorY;
				if (!inside(tx, ty))
					continue;
				boolean origin = tx == x && ty == y;
				switch (glyph) {
				case Material.ANY:
					break;
				case Material.EMPTY:
					nextCells[ty][tx] = 0;
					originWritten |= origin;
					break;
				case Material.PARTICLE:
					claimed = true;
					destinationX = tx;
					destinationY = ty;
					destinationValue = particle;
					break;
				case Pattern.SHARED:
					lastSharedX = tx;
					lastSharedY = ty;
					// Die with one face per "x": 0 wins. Once one wins the others are
					// not rolled any more
					if (!hit && (int) (Math.random() * step.shared) == 0) {
						claimed = true;
						destinationX = tx;
						destinationY = ty;
						destinationValue = particle;
						hit = true;
					}
					break;
				case Pattern.REPEATED:
					// One die per cell, all of them independent: the winners are a new
					// particle with one of the shares of the life that is left
					if ((int) (Math.random() * step.copies) == 0 && isFree(tx, ty, nextCells)) {
						int value = lifeOfCopy(particle, step.copies);
						if (value >= 0)
							nextCells[ty][tx] = value;
						originWritten |= origin;
					}
					break;
				case Pattern.WRAPS:
					claimed = true;
					destinationX = tx;
					destinationY = ty;
					destinationValue = Material.advanceWrapped(particle);
					break;
				case Pattern.CLAMPS:
					claimed = true;
					destinationX = tx;
					destinationY = ty;
					destinationValue = Material.advanceClamped(particle);
					break;
				case Pattern.BURNS:
					claimed = true;
					destinationX = tx;
					destinationY = ty;
					destinationValue = Material.burn(particle);
					break;
				case Pattern.FEEDS:
					claimed = true;
					destinationX = tx;
					destinationY = ty;
					destinationValue = Material.feed(particle);
					break;
				default:
					// A material glyph: the cell becomes a particle of that material
					Material material = Material.byGlyph(glyph);
					if (material != null && isFree(tx, ty, nextCells)) {
						nextCells[ty][tx] = material.spawn();
						originWritten |= origin;
					}
				}
			}
		}

		// Every "x" missed the dice: the last one takes the particle anyway
		if (!hit && lastSharedX >= 0) {
			claimed = true;
			destinationX = lastSharedX;
			destinationY = lastSharedY;
			destinationValue = particle;
		}

		if (claimed) {
			// The particle is written after the cells are emptied, and when another
			// particle took the destination this frame it stays where it was, so
			// none of them is lost
			if (!isFree(destinationX, destinationY, nextCells)) {
				destinationX = x;
				destinationY = y;
				destinationValue = particle;
			}
			nextCells[destinationY][destinationX] = destinationValue;
		} else if (!originWritten) {
			// Nothing claimed the particle and its cell is still empty: it stays
			nextCells[y][x] = particle;
		}
	}

	/**
	 * Shares out the life that is left in a particle between the copies that one
	 * "o" can make, so that a child never lives longer than its parent.
	 *
	 * @param value  the value of the particle that makes the copies
	 * @param copies the number of copies that share the life that is left
	 * @return the value of one copy, or -1 when there is no life left to share
	 */
	private static int lifeOfCopy(int value, int copies) {
		Material material = Material.ofValue(value);
		if (material == null || !material.life)
			return value; // without life there is nothing to share
		int share = (material.last() - value) / copies;
		if (share < 1)
			return -1; // too old to leave a child
		return material.last() - share;
	}

	/**
	 * Checks whether the match of a rule fits the neighbourhood of a cell.
	 * <p>
	 * The pattern is a small matrix centred on the cell: the middle row is its own
	 * row, the middle character the particle itself. The centre is computed from
	 * the size of the pattern, so any odd size works and the rules do not have to
	 * count columns. Characters that do not match make the method return
	 * immediately.
	 * </p>
	 *
	 * @param step  the rule whose match is checked, it also knows its centre
	 * @param x     the x coordinate of the cell to check
	 * @param y     the y coordinate of the cell to check
	 * @param cells the cell data to check against
	 * @return true if the pattern matches, false otherwise
	 */
	private boolean matches(Step step, int x, int y, int[][] cells) {
		String[] pattern = step.match;
		for (int py = 0; py < pattern.length; py++) {
			for (int px = 0; px < pattern[py].length(); px++) {
				char wanted = pattern[py].charAt(px);
				if (wanted == Material.ANY)
					continue; // this neighbour does not matter
				if (!matches(wanted, x + px - step.anchorX, y + py - step.anchorY, cells))
					return false;
			}
		}
		return true;
	}

	/**
	 * Checks one glyph of a match against one position.
	 * <p>
	 * An uppercase letter matches its whole band, a lowercase one only the last
	 * value of the band: the material is still there but it has spent its life, so
	 * the rule that transforms it catches it.
	 * </p>
	 *
	 * @param wanted the glyph of the match
	 * @param x      the x coordinate to inspect, may be outside the grid
	 * @param y      the y coordinate to inspect, may be outside the grid
	 * @param cells  the cell data
	 * @return true if the position looks like the glyph
	 */
	private boolean matches(char wanted, int x, int y, int[][] cells) {
		if (wanted == Material.PARTICLE)
			return glyphAt(x, y, cells) != Material.EMPTY;
		if (Character.isLowerCase(wanted)) {
			Material material = Material.byGlyph(Character.toUpperCase(wanted));
			return material != null && inside(x, y) && cells[y][x] == material.last();
		}
		return glyphAt(x, y, cells) == wanted;
	}

	/**
	 * What a position looks like to the pattern matcher.
	 * <p>
	 * Lateral and bottom borders count as a particle, so particles stay inside
	 * the canvas, while everything above the grid counts as empty, so particles
	 * can fall in from the top.
	 * </p>
	 *
	 * @param x     the x coordinate to inspect, may be outside the grid
	 * @param y     the y coordinate to inspect, may be outside the grid
	 * @param cells the cell data
	 * @return the glyph of the material of the position, a border counting as
	 *         {@link Material#PARTICLE} and empty space as {@link Material#EMPTY}
	 */
	private char glyphAt(int x, int y, int[][] cells) {
		if (x < 0 || x >= cols || y >= rows)
			return Material.PARTICLE; // lateral and bottom borders are solid
		if (y < 0)
			return Material.EMPTY; // above the grid counts as free space
		return Material.glyphOf[cells[y][x]];
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
	 * A step rule: the drawing of the neighbourhood that triggers it and the
	 * drawing of what that neighbourhood becomes.
	 * <p>
	 * Both drawings have the same shape and use the glyphs of {@link Pattern}, so
	 * a rule reads as two stacked pictures: how the neighbourhood looks now and
	 * how it looks after the step. The cell of the particle is the middle one of
	 * those pictures, which is why the size of the drawing can be anything odd
	 * without the rules saying where their centre is.
	 * </p>
	 */
	private static class Step {
		final String[] match;
		final String[] result;
		/** Column of the cell of the particle in both drawings. */
		final int anchorX;
		/** Row of the cell of the particle in both drawings. */
		final int anchorY;
		/** Number of {@code x} of the result: they share the particle between them. */
		final int shared;
		/** Number of {@code o} of the result: each one rolls its own dice. */
		final int copies;

		Step(String[] match, String[] result) {
			if (result.length != match.length)
				throw new IllegalArgumentException("The result must have as many rows as the match");
			for (int row = 0; row < match.length; row++)
				if (result[row].length() != match[row].length())
					throw new IllegalArgumentException("The result must have the same shape as the match");
			this.match = match;
			this.result = result;
			this.anchorX = match[0].length() / 2;
			this.anchorY = match.length / 2;
			this.shared = count(result, Pattern.SHARED);
			this.copies = count(result, Pattern.REPEATED);
		}

		/**
		 * Number of cells of a result drawn with one glyph.
		 * <p>
		 * It is both the faces of the die rolled for each one and what makes their
		 * chances add up to one. It only depends on the rule, so it is counted once
		 * here instead of on every step of every particle.
		 * </p>
		 *
		 * @param result the result of the rule
		 * @param glyph  the glyph to count
		 * @return the number of cells with that glyph, 0 when there is none
		 */
		private static int count(String[] result, char glyph) {
			int count = 0;
			for (String row : result)
				for (int px = 0; px < row.length(); px++)
					if (row.charAt(px) == glyph)
						count++;
			return count;
		}
	}

	/**
	 * The drawings used to decide what happens to a particle.
	 * <p>
	 * Each rule is written as rows of characters, so the drawing below is the rule
	 * itself. In a match:
	 * <ul>
	 *   <li>{@link Material#ANY}: the neighbour can be anything</li>
	 *   <li>{@link Material#PARTICLE}: any particle (borders count as one too)</li>
	 *   <li>{@link Material#EMPTY}: free space</li>
	 *   <li>a material glyph: a value of that band</li>
	 *   <li>a lowercase material: the last value of that band, so the material is
	 *       still there but it has spent its life</li>
	 * </ul>
	 * In a result the same glyphs say what gets written:
	 * <ul>
	 *   <li>{@link #SHARED}: the particle, shared between the {@code x} cells</li>
	 *   <li>{@link #REPEATED}: the particle, once per {@code o} cell that wins its
	 *       own dice</li>
	 *   <li>{@link #WRAPS}, {@link #CLAMPS}, {@link #BURNS}, {@link #FEEDS}: the
	 *       particle after walking its life</li>
	 *   <li>a material glyph: a new particle of that material</li>
	 * </ul>
	 * The centre of a drawing is its middle character, so in a match it is the
	 * particle being evaluated and in a result the cell that particle leaves.
	 * </p>
	 */
	private static class Pattern {
		/** Share the particle between these cells: only one of them wins. */
		private static final char SHARED = 'x';
		/** Roll the dice in each of these cells: several of them can win. */
		private static final char REPEATED = 'o';
		/** One step towards the end of the band, wrapping around at the end. */
		private static final char WRAPS = 'V';
		/** One step towards the end of the band, staying there when it ends. */
		private static final char CLAMPS = 'W';
		/** Half of the life that is left. */
		private static final char BURNS = 'r';
		/** Half of the life already spent. */
		private static final char FEEDS = 'l';

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

		/**
		 * Water on any of the eight sides of the fire puts it out: the flame is
		 * consumed where it is instead of rising or spreading, so a fire cannot
		 * jump over a drop of water.
		 * <p>
		 * The eight drawings are the same rule seen from each side, because a
		 * match says what each cell holds and cannot say "water anywhere around".
		 * They are also why the water does not need to be the one that kills the
		 * fire: a particle always writes itself at the end of its own step, so the
		 * flame is the only one that can decide not to come back.
		 * </p>
		 */
		private static final Step FIRE_DIES_UNDER_WATER_STEP = new Step(
				new String[] {
						"?A?",
						"?F?",
						"???" },
				new String[] {
						"???",
						"?_?",
						"???" });

		/** Water below puts the flame out. */
		private static final Step FIRE_DIES_OVER_WATER_STEP = new Step(
				new String[] {
						"???",
						"?F?",
						"?A?" },
				new String[] {
						"???",
						"?_?",
						"???" });

		/** Water at its left puts the flame out. */
		private static final Step FIRE_DIES_LEFT_STEP = new Step(
				new String[] {
						"???",
						"AF?",
						"???" },
				new String[] {
						"???",
						"?_?",
						"???" });

		/** Water at its right puts the flame out. */
		private static final Step FIRE_DIES_RIGHT_STEP = new Step(
				new String[] {
						"???",
						"?FA",
						"???" },
				new String[] {
						"???",
						"?_?",
						"???" });

		/** Water at the upper left puts the flame out. */
		private static final Step FIRE_DIES_UPPER_LEFT_STEP = new Step(
				new String[] {
						"A??",
						"?F?",
						"???" },
				new String[] {
						"???",
						"?_?",
						"???" });

		/** Water at the upper right puts the flame out. */
		private static final Step FIRE_DIES_UPPER_RIGHT_STEP = new Step(
				new String[] {
						"??A",
						"?F?",
						"???" },
				new String[] {
						"???",
						"?_?",
						"???" });

		/** Water at the lower left puts the flame out. */
		private static final Step FIRE_DIES_LOWER_LEFT_STEP = new Step(
				new String[] {
						"???",
						"?F?",
						"A??" },
				new String[] {
						"???",
						"?_?",
						"???" });

		/** Water at the lower right puts the flame out. */
		private static final Step FIRE_DIES_LOWER_RIGHT_STEP = new Step(
				new String[] {
						"???",
						"?F?",
						"??A" },
				new String[] {
						"???",
						"?_?",
						"???" });

		/**
		 * Fire that has spent its life becomes smoke. Nothing disappears on its
		 * own: the end of a life is always a rule that can be read.
		 */
		private static final Step FIRE_END_STEP = new Step(
				new String[] { "f" },
				new String[] { "H" });

		/** Smoke that has spent its life disappears. */
		private static final Step SMOKE_END_STEP = new Step(
				new String[] { "h" },
				new String[] { "_" });

		/** Wood that has spent its life catches fire. */
		private static final Step WOOD_END_STEP = new Step(
				new String[] { "m" },
				new String[] { "F" });

		/**
		 * Wood with fire above burns: it walks towards the end of its band one step
		 * per generation, so its colour tells how much wood is left. Fire on any of
		 * its four sides does the same, which is how a flame runs through a log.
		 */
		private static final Step WOOD_BURNS_ABOVE_STEP = new Step(
				new String[] {
						"?F?",
						"?M?",
						"???" },
				new String[] {
						"???",
						"?W?",
						"???" });

		/** Wood with fire below burns. */
		private static final Step WOOD_BURNS_BELOW_STEP = new Step(
				new String[] {
						"???",
						"?M?",
						"?F?" },
				new String[] {
						"???",
						"?W?",
						"???" });
		/** Wood with fire at its left burns. */
		private static final Step WOOD_BURNS_LEFT_STEP = new Step(
				new String[] {
						"???",
						"FM?",
						"???" },
				new String[] {
						"???",
						"?W?",
						"???" });
		/** Wood with fire at its right burns. */
		private static final Step WOOD_BURNS_RIGHT_STEP = new Step(
				new String[] {
						"???",
						"?MF",
						"???" },
				new String[] {
						"???",
						"?W?",
						"???" });

		/**
		 * Fire with wood below feeds on it: it recovers half of the life it has
		 * already spent, which is what keeps a flame alive while there is fuel.
		 */
		private static final Step FIRE_FEEDS_STEP = new Step(
				new String[] {
						"???",
						"?F?",
						"?M?" },
				new String[] {
						"???",
						"?l?",
						"???" });

		/**
		 * Wood does not fall: it is the only material that stays where it is put.
		 * The result is the particle and not the material, so the wood keeps the
		 * life it has already lost.
		 */
		private static final Step WOOD_STAYS_STEP = new Step(
				new String[] { "M" },
				new String[] { "X" });

		/** Fire with free space above rises one row, one step older. */
		private static final Step FIRE_RISES_STEP = new Step(
				new String[] {
						"?_?",
						"?F?",
						"???" },
				new String[] {
						"?W?",
						"?_?",
						"???" });

		/**
		 * Fire that cannot rise spreads: it ages in place and copies itself to the
		 * four cells around it, each one with one chance in four, so on average it
		 * leaves one flame behind and the burn neither grows nor dies on its own.
		 */
		private static final Step FIRE_SPREADS_STEP = new Step(
				new String[] {
						"???",
						"?F?",
						"???" },
				new String[] {
						"?o?",
						"oWo",
						"?o?" });

		/** Smoke rises like fire, but it neither spreads nor creates anything. */
		private static final Step SMOKE_RISES_STEP = new Step(
				new String[] {
						"?_?",
						"?H?",
						"???" },
				new String[] {
						"?W?",
						"?_?",
						"???" });

		/**
		 * Smoke that cannot rise ages where it is.
		 * <p>
		 * A material with life needs a rule that ages it even when it cannot move:
		 * the smoke of a fire that burnt under the sand has nowhere to go, and
		 * without this rule it would never reach its last value, so it would stay for
		 * ever as a grey grain holding the sand up.
		 * </p>
		 */
		private static final Step SMOKE_AGES_STEP = new Step(
				new String[] { "?H?" },
				new String[] { "?W?" });

		/**
		 * Water with a grain of sand on top of it stops being a drop for a while: it
		 * becomes the water that is holding the grain, a state with a band of its
		 * own that counts turns instead of a life.
		 * <p>
		 * Nothing is moved and nothing is consumed here. The same drop keeps its
		 * cell and turns into the mark, so the water of the world is the same; the
		 * grain rests on it as it rests on any particle, and the swap comes later.
		 * </p>
		 */
		private static final Step WATER_IS_HELD_STEP = new Step(
				new String[] {
						"?S?",
						"?A?",
						"???" },
				new String[] {
						"???",
						"?N?",
						"???" });

		/**
		 * The mark ages while the grain is still on top of it: one step towards the
		 * last value of its band per turn, and that walk is the wait of the grain.
		 * <p>
		 * The walk cannot go past the last value, so a grain that stays put for ever
		 * would keep the drop marked at its darkest; what bounds the wait is the
		 * size of the band, not the rule.
		 * </p>
		 */
		private static final Step HELD_AGES_STEP = new Step(
				new String[] {
						"?S?",
						"?N?",
						"???" },
				new String[] {
						"???",
						"?W?",
						"???" });

		/**
		 * Sand on top of a mark that has waited long enough sinks into it: it empties
		 * its own cell and claims the one below, which is where the drop was.
		 * <p>
		 * The two rules of the swap are written the way round that keeps both
		 * particles. This one empties the cell of the grain, so the water can climb
		 * into it, and the one of the water writes nothing in its own cell, so the
		 * grain that has just landed there is not wiped out. It works because the
		 * rows are walked from the top: the grain is always stepped first, and the
		 * cell it leaves is empty when the water asks for it.
		 * </p>
		 */
		private static final Step SAND_SINKS_STEP = new Step(
				new String[] {
						"???",
						"?S?",
						"?n?" },
				new String[] {
						"???",
						"?_?",
						"?X?" });

		/**
		 * The mark of a drop that has waited long enough climbs into the cell the
		 * grain has just left, and it climbs as a mark of the first value: {@code V}
		 * sends it back to the start of its band, so the next grain that lands on it
		 * waits exactly as long.
		 * <p>
		 * Without that the wait would be paid only once: from the second grain on the
		 * mark would be at its last value, every grain would sink in a single step
		 * and the column would fall as fast as in the air.
		 * </p>
		 */
		private static final Step HELD_RISES_STEP = new Step(
				new String[] {
						"?S?",
						"?n?",
						"???" },
				new String[] {
						"?V?",
						"???",
						"???" });

		/**
		 * A mark with no grain on top of it is a drop again: the sand slid away, so
		 * there is nothing left to swap with.
		 */
		private static final Step HELD_RELEASES_STEP = new Step(
				new String[] { "?N?" },
				new String[] { "?A?" });

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
		 * Water with fire above jumps into the cell the flame leaves: the drop
		 * follows the fire it has just put out instead of letting a hole open
		 * over it.
		 * <p>
		 * Only upwards, and only these three drawings, because the flame above is
		 * stepped first: it is already gone when the water is looked at, so the
		 * cell is free and nothing can be written over the drop. The rest of the
		 * holes need no rule, they are below or at the side and the water fills
		 * them falling and flowing like it fills any other.
		 * </p>
		 */
		private static final Step WATER_PUTS_OUT_ABOVE_STEP = new Step(
				new String[] {
						"?F?",
						"?A?",
						"???" },
				new String[] {
						"?X?",
						"?_?",
						"???" });

		/** Water with fire at the upper left jumps into the cell of the flame. */
		private static final Step WATER_PUTS_OUT_UPPER_LEFT_STEP = new Step(
				new String[] {
						"F??",
						"?A?",
						"???" },
				new String[] {
						"X??",
						"?_?",
						"???" });

		/** Water with fire at the upper right jumps into the cell of the flame. */
		private static final Step WATER_PUTS_OUT_UPPER_RIGHT_STEP = new Step(
				new String[] {
						"??F",
						"?A?",
						"???" },
				new String[] {
						"??X",
						"?_?",
						"???" });

		/**
		 * Water with free space at both sides flows to one of them: it levels its
		 * surface instead of piling up in a pyramid like sand.
		 * <p>
		 * It goes before the diagonal slides and after the free fall, so a drop that
		 * has landed opens sideways first and only rolls down a slope when it has no
		 * free side left. The row below is asked only for its own cell: the free fall
		 * has already been tried, so the space below is taken, and the diagonals only
		 * block the levelling over an uneven bed, which is where a liquid should level
		 * the most.
		 * </p>
		 */
		private static final Step WATER_FLOWS_BOTH_STEP = new Step(
				new String[] {
						"???",
						"_A_",
						"?X?" },
				new String[] {
						"???",
						"x_x",
						"???" });

		/** Water with free space only at its left flows there. */
		private static final Step WATER_FLOWS_LEFT_STEP = new Step(
				new String[] {
						"???",
						"_AX",
						"?X?" },
				new String[] {
						"???",
						"X_?",
						"???" });

		/** Water with free space only at its right flows there. */
		private static final Step WATER_FLOWS_RIGHT_STEP = new Step(
				new String[] {
						"???",
						"XA_",
						"?X?" },
				new String[] {
						"???",
						"?_X",
						"???" });

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
		 * Rules in the order they are tried: the first one that matches decides
		 * the step, so the table is a priority list.
		 * <p>
		 * The transitions of life go first, so a particle that has spent its life
		 * becomes what its rule says before it moves. Then every material that does
		 * not behave like a grain of sand says what it does, and after it the
		 * generic gravity that any particle obeys. The water putting out the fire
		 * goes before all of them: a flame that touches water never gets to rise
		 * or to spread.
		 * </p>
		 * <p>
		 * Inside the generic gravity the water levelling goes before the diagonal
		 * slides: a liquid has no angle of repose, so a drop that has landed opens
		 * sideways and only rolls down a slope when it has no free side left. With
		 * the diagonals first the water piles up in the same cone as the sand.
		 * </p>
		 * <p>
		 * The rules of the drop that is holding a grain go together and before the
		 * free fall: the mark is a state of the water and not a material of its
		 * own, so the water of a pond is still water, and what moves in the swap
		 * is the grain going down, not the drop going up. The one that climbs
		 * goes before the one that ages: the ageing rule matches the whole band,
		 * the last value included, so after it the mark of a drop that has waited
		 * would never rise.
		 * </p>
		 */
		private static final Step[] STEPS = {
				FIRE_DIES_UNDER_WATER_STEP,
				FIRE_DIES_OVER_WATER_STEP,
				FIRE_DIES_LEFT_STEP,
				FIRE_DIES_RIGHT_STEP,
				FIRE_DIES_UPPER_LEFT_STEP,
				FIRE_DIES_UPPER_RIGHT_STEP,
				FIRE_DIES_LOWER_LEFT_STEP,
				FIRE_DIES_LOWER_RIGHT_STEP,
				FIRE_END_STEP,
				SMOKE_END_STEP,
				WOOD_END_STEP,
				WOOD_BURNS_ABOVE_STEP,
				WOOD_BURNS_BELOW_STEP,
				WOOD_BURNS_LEFT_STEP,
				WOOD_BURNS_RIGHT_STEP,
				FIRE_FEEDS_STEP,
				WOOD_STAYS_STEP,
				FIRE_RISES_STEP,
				FIRE_SPREADS_STEP,
				SMOKE_RISES_STEP,
				SMOKE_AGES_STEP,
				WATER_PUTS_OUT_ABOVE_STEP,
				WATER_PUTS_OUT_UPPER_LEFT_STEP,
				WATER_PUTS_OUT_UPPER_RIGHT_STEP,
				WATER_IS_HELD_STEP,
				HELD_RISES_STEP,
				HELD_AGES_STEP,
				SAND_SINKS_STEP,
				HELD_RELEASES_STEP,
				FREE_FALL_STEP,
				WATER_FLOWS_BOTH_STEP,
				WATER_FLOWS_LEFT_STEP,
				WATER_FLOWS_RIGHT_STEP,
				SLIDE_BOTH_STEP,
				SLIDE_LEFT_STEP,
				SLIDE_RIGHT_STEP,
				STAY_STEP };
	}

}
