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
 *   <li>The acid: a liquid that etches the wood it touches, spending itself
 *       one bite per cell, and that becomes water when it runs into water</li>
 *   <li>The slow sink of the sand: a drop of water with a grain on top of it
 *       holds it for a moment, and then the two swap places</li>
 *   <li>The steam of a quench: a flame that touches water spends the drop and
 *       leaves smoke in its place, whichever side the drop came from</li>
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
	 * Changes places with the particle of another cell.
	 * <p>
	 * The two halves are written here, from the same picture of the world: the
	 * particle goes to the cell the rule points at, and that cell gives the
	 * particle it holds in exchange, so neither of them sees the result of the
	 * other. The cell that receives the particle still has its turn ahead, and
	 * its own rule would write over what is left there; it does not take that
	 * turn, because the pass leaves alone any cell that already has something in
	 * the next generation (see {@link WorldRenderer}).
	 * </p>
	 * <p>
	 * The particle of the other cell does not always climb into the cell this
	 * one leaves: it does so when that cell has no way out to a side, and
	 * otherwise it leaves through the side it has open and the cell is emptied. A
	 * particle with room around it is squeezed out instead of pushed up, which is
	 * what keeps the water from rising through a column of sand in the air: the
	 * column is one cell wide, so every cell of it has a side open and the drop
	 * leaves there instead of going up to the top.
	 * </p>
	 * <p>
	 * The cell it points at can be in the row below, which the pass has not read
	 * yet, or in its own row, which it may have read already: there the other
	 * particle is not in the next generation unless it wrote itself there, and
	 * that is what is asked for, so an exchange with a cell that has already
	 * moved away cancels instead of copying the particle that left. That is the
	 * same criterion the writing of a new material uses (see
	 * {@link #canHoldNewMaterial}), and the value it takes is the one of the next
	 * generation when the cell already wrote itself, so a drop that is walking
	 * its band keeps the value it has reached.
	 * </p>
	 * <p>
	 * When the cell it points at was already taken, the exchange does not happen
	 * and the particle stays where it was, which is the same that any other rule
	 * does when it finds its destination occupied.
	 * </p>
	 *
	 * @param x           column of the particle that changes places
	 * @param y           row of the particle that changes places
	 * @param targetX     column of the cell it changes places with, below it or at
	 *                    its side
	 * @param targetY     row of the cell it changes places with, below it or at its
	 *                    side
	 * @param cells       the current cell data
	 * @param nextCells   the next cell data (to be updated)
	 * @param rightToLeft true when the pass is reading the rows from right to left
	 */
	private void exchange(int x, int y, int targetX, int targetY, int[][] cells, int[][] nextCells,
			boolean rightToLeft) {
		int particle = cells[y][x];
		if (!canGiveItsParticle(targetX, targetY, x, y, cells, nextCells, rightToLeft)) {
			// Somebody arrived at that cell first, or its particle left: the
			// particle is kept where it was, so nothing is lost
			nextCells[y][x] = particle;
			return;
		}
		// The value is read before the particle takes the cell: a cell the pass has
		// already read wrote its own value there, and that is the one it keeps
		int other = nextCells[targetY][targetX] != 0 ? nextCells[targetY][targetX] : cells[targetY][targetX];
		nextCells[targetY][targetX] = particle;
		int escape = freeSide(x, y, cells, nextCells);
		if (escape < 0)
			nextCells[y][x] = other;
		else
			nextCells[y][escape] = other;
	}

	/**
	 * Checks whether the cell a swap points at can still give the particle the
	 * match saw in it.
	 * <p>
	 * A cell the pass has not reached yet is still free in the next generation and
	 * gives whatever the match saw; a cell it has already read gives its particle
	 * only when that particle is still written there, which is why a swap can look
	 * at its own row without copying a drop that has already moved away.
	 * </p>
	 *
	 * @param x           column of the cell the swap points at
	 * @param y           row of the cell the swap points at
	 * @param particleX   column of the particle that changes places
	 * @param particleY   row of the particle that changes places
	 * @param cells       the current cell data
	 * @param nextCells   the next cell data
	 * @param rightToLeft true when the pass is reading the rows from right to left
	 * @return true when the cell can give its particle in the exchange
	 */
	private boolean canGiveItsParticle(int x, int y, int particleX, int particleY, int[][] cells, int[][] nextCells,
			boolean rightToLeft) {
		if (!readYet(x, y, particleX, particleY, rightToLeft))
			return isFree(x, y, nextCells);
		return stillHoldsTheSameParticle(x, y, cells, nextCells);
	}

	/**
	 * Side of a cell that is empty in this generation and still empty in the next
	 * one, so that the particle an exchange displaces has somewhere to leave by.
	 *
	 * @param x         column of the cell
	 * @param y         row of the cell
	 * @param cells     the current cell data
	 * @param nextCells the next cell data
	 * @return the column of a free side, or -1 when both sides are taken
	 */
	private int freeSide(int x, int y, int[][] cells, int[][] nextCells) {
		boolean left = x > 0 && cells[y][x - 1] == 0 && nextCells[y][x - 1] == 0;
		boolean right = x < cols - 1 && cells[y][x + 1] == 0 && nextCells[y][x + 1] == 0;
		if (left && right)
			return Math.random() < 0.5 ? x - 1 : x + 1;
		if (left)
			return x - 1;
		return right ? x + 1 : -1;
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
	 * <p>
	 * A hit is a force of the mouse and not a turn of the world, so it moves what
	 * is loose: the materials of {@link Material#ANCHORED} are put in place and
	 * stay there, and they are not even asked for the dice. They also hold the
	 * particles behind them, because the cell of something that does not move is
	 * never free and a push only takes a free side.
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
		// A hit is a force of the mouse: it moves what is loose, so a material
		// that is put in place is not even asked for the dice
		if (!Material.isAnchored(cells[y][x])) {
			// Roll dice to determine if this particle is affected by the hit
			// force. Higher hitForce = higher probability of being affected
			// (hitForce / MAX_HIT_FORCE chance)
			int dice = (int) (Math.random() * MAX_HIT_FORCE);
			if (dice < hitForce) {
				Step step = hitDirection == RIGHT ? Pattern.PUSH_RIGHT_STEP : Pattern.PUSH_LEFT_STEP;
				if (matches(step, x, y, cells)) {
					// Columns are read the way the hit pushes them
					applyPattern(step, x, y, cells, nextCells, hitDirection == RIGHT, false);
					return;
				}
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
	 * @param x           the x coordinate of the particle
	 * @param y           the y coordinate of the particle
	 * @param cells       the current cell data
	 * @param nextCells   the next cell data (to be updated)
	 * @param rightToLeft true when the pass is reading each row from right to
	 *                    left, which decides which neighbours have already had
	 *                    their turn
	 * @param aging       true when this turn also spends a value of the band, so
	 *                    an age slower than the clock lets the particle move
	 *                    without growing older
	 */
	public void stepParticle(int x, int y, int[][] cells, int[][] nextCells, boolean rightToLeft, boolean aging) {
		for (Step step : Pattern.STEPS) {
			if (matches(step, x, y, cells)) {
				applyPattern(step, x, y, cells, nextCells, rightToLeft, aging);
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
	 *       the particle and the rest stay empty: one die is thrown for the whole
	 *       rule and it is the cell it points at that takes it</li>
	 *   <li>{@link Pattern#REPEATED}: the particle is copied in each of these
	 *       cells with one chance in as many as there are, and each one rolls its
	 *       own dice, so several of them can win: this is how a material is
	 *       created and not only moved</li>
	 *   <li>{@link Pattern#WRAPS}, {@link Pattern#CLAMPS}, {@link Pattern#BURNS}
	 *       and {@link Pattern#FEEDS}: like a particle, but written with its value
	 *       one step older, one step older without wrapping, half of the life it
	 *       has left older, or half of the life it has spent younger. Only the
	 *       age of {@code W} waits for the turn of {@link Material#isAgingDue}, so
	 *       a short band can move in every turn and still last long; the burns
	 *       and the feeds are events of the rule, not the pass of time, and do not
	 *       wait</li>
	 *   <li>a material glyph: a new particle of that material is created in the
	 *       cell when it is free, or in the place of the particle that is
	 *       already there when the cell belongs to a neighbour (see
	 *       {@link #canHoldNewMaterial})</li>
	 *   <li>{@link Pattern#SWAPS}: the particle and the one in this cell change
	 *       places, both written here at once; the one in this cell only goes up
	 *       when it has no way out to a side (see {@link #exchange})</li>
	 *   <li>{@link Pattern#SHARED_SWAPS}: like {@code SWAPS}, with the cells of
	 *       this glyph sharing the exchange, one chance in as many as there are
	 *       each</li>
	 *   <li>{@link Material#ANY}: nothing is written</li>
	 * </ul>
	 * The {@code ?} cells need no copy: a neighbour holding a particle is a
	 * particle of its own that moves in its own step, and copying it here would
	 * leave a second copy in the cell it just left.
	 * </p>
	 * <p>
	 * Writing a material in a cell that is not the cell of the particle is the
	 * only way a rule takes the place of another particle, and it never creates
	 * one nor loses one: the cell has to be holding the particle the match saw,
	 * either because the sweep has not read it yet or because that particle is
	 * still written there in the next generation. That is what lets a flame
	 * leave smoke on the drop that puts it out, from any of the eight sides,
	 * without touching the count of particles.
	 * </p>
	 * <p>
	 * The particle itself is written once, when the pass is over, in the cell that
	 * claimed it. When no glyph claimed it and its own cell was left empty, it
	 * stays where it was; when the rule wrote something else in its cell, the
	 * particle is consumed, which is how a material becomes another one.
	 * </p>
	 *
	 * @param step        the rule being applied, its result is what gets written
	 * @param x           the x coordinate of the particle
	 * @param y           the y coordinate of the particle
	 * @param cells       the current cell data
	 * @param nextCells   the next cell data (to be updated)
	 * @param rightToLeft true when the pass is reading the rows from right to left
	 * @param aging       true when the turn also spends a value of the band
	 */
	private void applyPattern(Step step, int x, int y, int[][] cells, int[][] nextCells, boolean rightToLeft,
			boolean aging) {
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

		// The cells that share something throw one die for the whole rule, and it
		// is thrown before they are read: thrown one by one, the first would win
		// one in as many, the second one in that same share of what is left, and
		// the last would take the rest. With two faces that still comes out half
		// and half, and with more it loads the last cell. A cell the pass skips
		// because the grid ends there is not one of the faces, so a rule has to
		// keep its sharing cells inside
		int sharedPick = step.shared > 0 ? (int) (Math.random() * step.shared) : -1;
		int sharedSeen = 0;
		int swapPick = step.swapShares > 0 ? (int) (Math.random() * step.swapShares) : -1;
		int swapSeen = 0;
		int swapX = -1; // cell the exchange points at, if the rule has one
		int swapY = -1;

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
					// The cell of the particle is always emptied; a neighbour only
					// when its own particle is still written there
					if (origin || stillHoldsTheSameParticle(tx, ty, cells, nextCells)) {
						nextCells[ty][tx] = 0;
						originWritten |= origin;
					}
					break;
				case Material.PARTICLE:
					claimed = true;
					destinationX = tx;
					destinationY = ty;
					destinationValue = particle;
					break;
				case Pattern.SHARED:
					if (sharedSeen++ == sharedPick) {
						claimed = true;
						destinationX = tx;
						destinationY = ty;
						destinationValue = particle;
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
					destinationValue = aging ? Material.advanceClamped(particle) : particle;
					break;
				case Pattern.BORN:
					claimed = true;
					destinationX = tx;
					destinationY = ty;
					destinationValue = Material.reborn(particle);
					break;
				case Pattern.SWAPS:
					swapX = tx;
					swapY = ty;
					break;
				case Pattern.SHARED_SWAPS:
					if (swapSeen++ == swapPick) {
						swapX = tx;
						swapY = ty;
					}
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
					// A material glyph: the cell becomes a particle of that material.
					// In the cell of the particle it needs free space; away from home
					// it only takes the place of a particle that is already there
					Material material = Material.byGlyph(glyph);
					boolean writable = origin ? isFree(tx, ty, nextCells)
							: canHoldNewMaterial(tx, ty, x, y, cells, nextCells, rightToLeft);
					if (material != null && writable) {
						nextCells[ty][tx] = material.spawn();
						originWritten |= origin;
					}
				}
			}
		}

		if (swapX >= 0) {
			// A swap writes both halves, so the particle is not kept in its own
			// cell first: the exchange takes its place
			exchange(x, y, swapX, swapY, cells, nextCells, rightToLeft);
		} else if (claimed) {
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
	 * Checks whether a rule can put a new material in a cell that is not the cell
	 * of the particle.
	 * <p>
	 * The cells the pass has not read yet still hold the particle the match saw,
	 * and writing the material there is what makes them skip their turn, exactly
	 * like a swap does. That is safe in any of them, whether they are below the
	 * particle or at its side: the rows are read from top to bottom, and the row
	 * of the particle is read in one direction or the other depending on the
	 * frame, so the pass has to be told which side it has already left behind
	 * (see {@link #readYet}). A cell it has already read only takes the material
	 * when its own particle is still written there in the next generation: if it
	 * moved away, or if somebody else took the cell, the rule writes nothing.
	 * </p>
	 *
	 * @param x           column of the cell the rule writes
	 * @param y           row of the cell the rule writes
	 * @param particleX   column of the particle being stepped
	 * @param particleY   row of the particle being stepped
	 * @param cells       the current cell data
	 * @param nextCells   the next cell data
	 * @param rightToLeft true when the pass is reading the rows from right to left
	 * @return true when the material glyph can write in that cell
	 */
	private boolean canHoldNewMaterial(int x, int y, int particleX, int particleY, int[][] cells, int[][] nextCells,
			boolean rightToLeft) {
		if (!readYet(x, y, particleX, particleY, rightToLeft))
			return isFree(x, y, nextCells) && cells[y][x] != 0;
		return stillHoldsTheSameParticle(x, y, cells, nextCells);
	}

	/**
	 * Checks whether the pass has already had its turn in a cell of this step.
	 * <p>
	 * The rows are always read from top to bottom; inside a row the direction
	 * depends on the frame and it swaps every frame, so that no particle is
	 * always the first to write. A cell the pass has not reached yet is still
	 * free in the next generation and can be taken by the rule of a particle that
	 * is being read before it.
	 * </p>
	 *
	 * @param x           column of the cell to check
	 * @param y           row of the cell to check
	 * @param particleX   column of the particle being stepped
	 * @param particleY   row of the particle being stepped
	 * @param rightToLeft true when the pass is reading the rows from right to left
	 * @return true when the pass has already been in that cell
	 */
	private static boolean readYet(int x, int y, int particleX, int particleY, boolean rightToLeft) {
		if (y != particleY)
			return y < particleY;
		return rightToLeft ? x > particleX : x < particleX;
	}

	/**
	 * Checks whether a cell the sweep has already read still holds the particle
	 * the match saw.
	 * <p>
	 * The comparison is by material and not by value, because the particle may
	 * have written itself with another value of its band in this same step,
	 * which is what {@code V}, {@code W}, {@code r}, {@code l} and {@code B} do.
	 * A cell left free by its particle and a cell taken by a particle of another
	 * material both fail, so a rule that replaces never creates one and never
	 * destroys anybody.
	 * </p>
	 *
	 * @param x         column of the cell
	 * @param y         row of the cell
	 * @param cells     the current cell data
	 * @param nextCells the next cell data
	 * @return true when the cell still holds a particle of the same material
	 */
	private static boolean stillHoldsTheSameParticle(int x, int y, int[][] cells, int[][] nextCells) {
		int there = nextCells[y][x];
		return there != 0 && Material.glyphOf[there] == Material.glyphOf[cells[y][x]];
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
		/** Number of {@code p} of the result: they share the exchange. */
		final int swapShares;

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
			this.swapShares = count(result, Pattern.SHARED_SWAPS);
			if (count(result, Pattern.SWAPS) > 1)
				throw new IllegalArgumentException("A result can only exchange places with one cell");
			// A swap writes the cell it points at, and the pass only leaves alone
			// the cells it has not read yet, or the ones that are still holding
			// their own particle: that is why a swap can look down or to a side,
			// but never up
			for (int row = 0; row < result.length; row++)
				for (int px = 0; px < result[row].length(); px++)
					if (row < anchorY
							&& (result[row].charAt(px) == Pattern.SWAPS || result[row].charAt(px) == Pattern.SHARED_SWAPS))
						throw new IllegalArgumentException("A swap can only point at the row of the particle or below it");
		}

		/**
		 * Number of cells of a result drawn with one glyph.
		 * <p>
		 * It is both the faces of the die thrown for the whole rule and what makes
		 * their chances add up to one. It only depends on the rule, so it is
		 * counted once here instead of on every step of every particle.
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
	 *       particle after walking its life, and {@link #BORN}: the particle
	 *       drawn again among the values a new one could take</li>
	 *   <li>{@link #SWAPS} and {@link #SHARED_SWAPS}: the particle and the one in
	 *       that cell change places, both written at once; the one in that cell
	 *       only goes up when it has no free side to leave by</li>
	 *   <li>a material glyph: a new particle of that material, either in the free
	 *       space of its cell or in the place of a particle the pass has not read
	 *       yet, or whose particle is still written there, at any side of it</li>
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
		/** The particle again, with one of the values a new one could take. */
		private static final char BORN = 'B';
		/** The particle and the one in this cell change places. */
		private static final char SWAPS = 'P';
		/** Like {@code P}, with the cells sharing the exchange between them. */
		private static final char SHARED_SWAPS = 'p';
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
		 * <p>
		 * In all eight the drop is spent as well: the smoke takes its place, which
		 * is the steam of the quench. One drop becomes one puff wherever it touched
		 * the flame, so the drop is never lost for nothing and the count of water
		 * plus smoke around a flame does not move. The write is safe in a cell the
		 * pass has not read yet, and in a cell it has already read the drop has
		 * written itself there, so the smoke replaces it and is not invented; a drop
		 * that flowed away in this very step is not spent.
		 * </p>
		 */

		/** Water above puts the flame out and leaves steam in the place of the drop. */
		private static final Step FIRE_DIES_UNDER_WATER_STEP = new Step(
				new String[] {
						"?A?",
						"?F?",
						"???" },
				new String[] {
						"?H?",
						"?_?",
						"???" });

		/** Water below puts the flame out and leaves steam in the place of the drop. */
		private static final Step FIRE_DIES_OVER_WATER_STEP = new Step(
				new String[] {
						"???",
						"?F?",
						"?A?" },
				new String[] {
						"???",
						"?_?",
						"?H?" });

		/** Water at its left puts the flame out and leaves steam in its place. */
		private static final Step FIRE_DIES_LEFT_STEP = new Step(
				new String[] {
						"???",
						"AF?",
						"???" },
				new String[] {
						"???",
						"H_?",
						"???" });

		/** Water at its right puts the flame out and leaves steam in its place. */
		private static final Step FIRE_DIES_RIGHT_STEP = new Step(
				new String[] {
						"???",
						"?FA",
						"???" },
				new String[] {
						"???",
						"?_H",
						"???" });

		/** Water at the upper left puts the flame out and leaves steam there. */
		private static final Step FIRE_DIES_UPPER_LEFT_STEP = new Step(
				new String[] {
						"A??",
						"?F?",
						"???" },
				new String[] {
						"H??",
						"?_?",
						"???" });

		/** Water at the upper right puts the flame out and leaves steam there. */
		private static final Step FIRE_DIES_UPPER_RIGHT_STEP = new Step(
				new String[] {
						"??A",
						"?F?",
						"???" },
				new String[] {
						"??H",
						"?_?",
						"???" });

		/** Water at the lower left puts the flame out and leaves steam there. */
		private static final Step FIRE_DIES_LOWER_LEFT_STEP = new Step(
				new String[] {
						"???",
						"?F?",
						"A??" },
				new String[] {
						"???",
						"?_?",
						"H??" });

		/** Water at the lower right puts the flame out and leaves steam there. */
		private static final Step FIRE_DIES_LOWER_RIGHT_STEP = new Step(
				new String[] {
						"???",
						"?F?",
						"??A" },
				new String[] {
						"???",
						"?_?",
						"??H" });

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
		 * Fire with wood above feeds on it as well: the fuel it has to eat can be on
		 * either side of the flame, so a log lit from below burns up as evenly as one
		 * lit from above.
		 * <p>
		 * Without this drawing a flame under a log could only grow old: it cannot
		 * rise, because the wood is over it, and it has nothing to eat, so it spends
		 * its life where it is and turns into smoke before the wood above it has
		 * finished burning. The fuel of a fire is the fuel it can reach, and from
		 * below the flame reaches it just the same.
		 * </p>
		 */
		private static final Step FIRE_FEEDS_ABOVE_STEP = new Step(
				new String[] {
						"?M?",
						"?F?",
						"???" },
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
		 * Water with a grain of sand on top of it waits: it walks towards the end
		 * of its band one step per turn, and that walk is the wait of the grain.
		 * <p>
		 * Nothing is moved here. The drop keeps its cell and only changes shade,
		 * and its band is laid out so that the shade barely changes while it
		 * waits, so the pool does not darken where a grain is resting. What
		 * bounds the wait is the size of the band and the clock of the water, not
		 * the rule.
		 * </p>
		 */
		private static final Step WATER_WAITS_UNDER_SAND_STEP = new Step(
				new String[] {
						"?S?",
						"?A?",
						"???" },
				new String[] {
						"???",
						"?W?",
						"???" });

		/**
		 * A grain over water that has waited long enough changes place with one of
		 * the three drops under it, drawn at random: straight down, down-left or
		 * down-right.
		 * <p>
		 * The wait is still the drop directly below, the one that has been walking
		 * its band under the grain, so a row still costs one wait. What changes is
		 * where the grain lands: the three destinations are offered to the same
		 * die, each one with one chance in three, and the drop that wins takes the
		 * cell the grain leaves, or a free side of it when it has one, so the pool
		 * keeps its variety, because the drop that moves keeps the shade it had.
		 * </p>
		 * <p>
		 * The exchange is written with {@code p} and not with a destination glyph
		 * of its own: neither side can be written by a glyph of the rule, because
		 * the exchange writes both. The {@code ?} of the middle is the cell the
		 * grain leaves, which the exchange takes care of.
		 * </p>
		 */
		private static final Step SAND_SINKS_SIDEWAYS_INTO_WATER_STEP = new Step(
				new String[] {
						"???",
						"?S?",
						"AaA" },
				new String[] {
						"???",
						"???",
						"ppp" });

		/**
		 * The same exchange when the two diagonals are not water, at the wall of
		 * the pool or on a pile: the grain goes straight down without choosing.
		 * <p>
		 * Without this rule a grain held by a drop that had finished its wait
		 * would stay there for ever, because the three-way rule needs water at
		 * both diagonals and the wait of the drop would never be paid.
		 * </p>
		 */
		private static final Step SAND_SINKS_STRAIGHT_INTO_WATER_STEP = new Step(
				new String[] {
						"???",
						"?S?",
						"?a?" },
				new String[] {
						"???",
						"???",
						"?P?" });

		/**
		 * A grain that has another grain just below it rolls down to a diagonal
		 * when there is water there: the two change places, so a pile of sand inside
		 * a pond collapses into a cone instead of growing as a column.
		 * <p>
		 * The generic slides ask for free space at the diagonal, and inside the water
		 * there is water, not free space, so a grain that landed on another one had
		 * nowhere to go: below it there was sand and not the drop of the wait, and
		 * the table ended in the stacking. Under the water the grain does what it
		 * does in the air, only paying with an exchange: it goes down to the
		 * diagonal and the drop it displaces comes up to its cell.
		 * </p>
		 * <p>
		 * The three rules go after the two sinkings, which are the ones that fit
		 * when what is below is water, and before the generic slides, which are the
		 * ones that fit when the diagonals are free. They are the same three
		 * drawings as {@code SLIDE_BOTH}, {@code SLIDE_LEFT} and
		 * {@code SLIDE_RIGHT}, read from the water: the cell in the diagonal has to
		 * be a drop for the exchange to give a drop and take nothing away.
		 * </p>
		 */
		private static final Step SAND_ROLLS_SIDEWAYS_IN_WATER_STEP = new Step(
				new String[] {
						"???",
						"?S?",
						"ASA" },
				new String[] {
						"???",
						"???",
						"p?p" });

		/** The same roll when only the left-down diagonal is water. */
		private static final Step SAND_ROLLS_LEFT_IN_WATER_STEP = new Step(
				new String[] {
						"???",
						"?S?",
						"ASX" },
				new String[] {
						"???",
						"???",
						"p??" });

		/** The same roll when only the right-down diagonal is water. */
		private static final Step SAND_ROLLS_RIGHT_IN_WATER_STEP = new Step(
				new String[] {
						"???",
						"?S?",
						"XSA" },
				new String[] {
						"???",
						"???",
						"??p" });

		/**
		 * A drop that reached the end of its band and no longer has a grain on top
		 * of it starts again: the sand slid away, so there is nothing left to
		 * swap with, and the drop would keep the flat shade of the wait for ever.
		 * <p>
		 * Written as a new particle for the same reason as any other birth: it
		 * comes back with one of the shades a drop is born with, so the pool keeps
		 * the variety it has and does not lighten where a grain passed.
		 * </p>
		 */
		private static final Step WATER_FORGETS_THE_WAIT_STEP = new Step(
				new String[] { "?a?" },
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
		 * Water with a flame just above it puts the flame out at once, without
		 * waiting for the turn of the flame's clock: the steam is written in its own
		 * cell and the cell of the flame is left empty.
		 * <p>
		 * It is the same result the flame writes when its own turn comes (see the
		 * {@code FIRE_DIES} rules), so the two never fight over the cells: whichever
		 * of them is stepped first, the drop ends up as one puff and the flame is
		 * gone. The cell of the flame is only emptied when the flame is still written
		 * there in the next generation, so a flame that has already flown away, or
		 * one whose cell was taken by another particle, is not touched.
		 * </p>
		 * <p>
		 * Only upwards, and only these three drawings, because the row of the flame
		 * is read before the row of the drop: from below the drop has the flame's
		 * cell still ahead of it. A drop above the flame cannot do this, and there
		 * the flame puts itself out when its own turn comes.
		 * </p>
		 */
		private static final Step WATER_PUTS_OUT_ABOVE_STEP = new Step(
				new String[] {
						"?F?",
						"?A?",
						"???" },
				new String[] {
						"?_?",
						"?H?",
						"???" });

		/** Water with the flame at the upper left squeezes it out and leaves steam. */
		private static final Step WATER_PUTS_OUT_UPPER_LEFT_STEP = new Step(
				new String[] {
						"F??",
						"?A?",
						"???" },
				new String[] {
						"_??",
						"?H?",
						"???" });

		/** Water with the flame at the upper right squeezes it out and leaves steam. */
		private static final Step WATER_PUTS_OUT_UPPER_RIGHT_STEP = new Step(
				new String[] {
						"??F",
						"?A?",
						"???" },
				new String[] {
						"??_",
						"?H?",
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
		 * The acid is the first material that pays for what it does, and the third
		 * use of a band: after the life that a rule walks and the wait that holds a
		 * grain, the acid shows the band as the price of a reaction. Its values are
		 * a charge and not time: each cell of wood takes one, falling and flowing
		 * take none, and a pool of it in the open is stable for ever. A drop is
		 * therefore worth a handful of cells and no more, so the acid etches the wood
		 * it touches instead of dissolving a forest.
		 * <p>
		 * The acid is heavier than the water, and that is a rule like any other: a
		 * drop with water below changes places with it, so a pour goes down to the
		 * bottom of a pond instead of resting on the surface. Without it the drop
		 * floated, dissolved into the water it touched and never reached the wood
		 * underneath. Going down costs nothing, so the charge arrives whole.
		 * </p>
		 * <p>
		 * Going down has two drawings. When the three cells below are water the drop
		 * goes to one of them drawn at equal parts; when the sides are not, it goes
		 * straight down. The draw is the same one the grain of sand uses to sink, and
		 * it is there for the look: a drop that always falls on the cell it has below
		 * cuts the pond with columns of one cell of acid, perfectly straight, which
		 * reads as a thread and not as a liquid. With the draw the pour opens while it
		 * goes down and reaches the bottom spread out, the way the heavy liquid of the
		 * world does. It costs nothing either: the three destinations are the row the
		 * sweep has not read yet, and the change takes one drop of water up, no more.
		 * </p>
		 * <p>
		 * The two reactions are paid at two speeds, and that is the why of the two
		 * glyphs: the corroding rules write {@code r}, which charges the bite at once,
		 * and the diluting ones write {@code W}, which waits for the age. A drop that
		 * bites spends a value per turn; a drop that water is eating spends one every
		 * hundred and twenty turns. Biting is an event, dissolving is a wait, and the
		 * same band serves both because the band is the money and not the clock.
		 * </p>
		 * <p>
		 * Neutralising is a process and not a step: water does not turn the acid into
		 * water, it eats it. The drop that touches water walks its band to the end and
		 * only then becomes water, and the water that did the eating is left as it is in
		 * every value of the way, so a pond of acid dissolves from the surface at the
		 * speed of the charge instead of flashing into water. With the neutralisation
		 * done in a single step, a drop of water in a puddle of acid was the end of the
		 * puddle: the water it wrote touched the acid next to it and the whole pond went
		 * in a handful of frames. Measured: thirty drops at the bottom of a pond of
		 * five hundred and seventy of water started paying towards the four hundred and
		 * eightieth step, the outer layer went at the six hundredth, and the whole
		 * puddle was water between the twelve hundredth and the eighteen hundredth, the
		 * count of drops coming out whole.
		 * </p>
		 * <p>
		 * The wood leaves smoke, which is what the chemistry gives: the acid eats the
		 * wood and the wood goes up. The drop pays a bite and goes on, and when it has
		 * nothing left it becomes water, so the acid ends neutralised whether water ate
		 * it or it ate the wood. That end was tried before with the neutralisation done
		 * in a single step, and it destroyed the material: the water of a spent drop
		 * touched the acid around it and the whole pond turned into water before a pour
		 * had landed. With the drop paying its charge to the water, the end is one drop
		 * and the water it leaves has to eat the next, so the pond survives the first
		 * death and dissolves from the surface. Measured: a pour of two hundred drops
		 * ate between ninety-seven and a hundred and seventeen cells of a log of four
		 * hundred and forty in four hundred steps, and two thousand steps later, with
		 * the pour almost spent, between a hundred and fifty-eight and a hundred and
		 * eighty-four, and every drop of it water.
		 * </p>
		 * <p>
		 * The four drawings are written from the acid and not from the wood, and
		 * that is not a detail: the glyphs that walk a life ({@code r}, {@code W},
		 * {@code l}...) always write the particle of the centre somewhere, so one of
		 * them in the cell of a neighbour moves the neighbour instead of ageing it.
		 * Whoever pays is the one in the middle. The smoke goes in the cell of the
		 * wood as a material glyph away from home, which takes the place of a
		 * particle that is already there, the same mechanism as the steam of the
		 * quench.
		 * </p>
		 * <p>
		 * The order of the group is the order of the drop's life: first it goes down
		 * through the water —straight or sideways, whichever the drawing allows—, then
		 * it bites the wood it lands on, and only when it can do neither is it still
		 * long enough for the water to eat it. That is why the
		 * diluting rules go after the corroding ones: an acid that reaches the wood
		 * inside a pond has to etch it even though it is surrounded by water, which is
		 * the whole reason it went down. With the other order a single drop of water
		 * at its side was enough to freeze it, and a pond of acid could sit on a plank
		 * for ever without leaving a mark.
		 * </p>
		 * <p>
		 * The order between the four decides which side is eaten when the drop has
		 * wood on more than one, because the table is a priority list: the side
		 * below goes first, since that is the side a drop of liquid rests on. And
		 * the corrosion goes before the levelling, so a drop that touches wood eats
		 * it instead of flowing away from it.
		 * </p>
		 * <p>
		 * One cell is one bite, but when two drops see the same cell of wood in the
		 * same step only one smoke is written and both bites are paid: the count of
		 * particles is kept, the charge of that corner is not. It is also what keeps
		 * a pool from eating through a log: the smoke of a bite has nowhere to go
		 * under the acid, so it sits in the cell it was born in until it ages away
		 * and the drops above have to wait for it.
		 * </p>
		 */

		/** Acid with wood below: the wood becomes smoke and the drop pays a bite. */
		private static final Step ACID_CORRODES_BELOW_STEP = new Step(
				new String[] {
						"???",
						"?C?",
						"?M?" },
				new String[] {
						"???",
						"?r?",
						"?H?" });

		/** Acid with wood above: the wood becomes smoke and the drop pays a bite. */
		private static final Step ACID_CORRODES_ABOVE_STEP = new Step(
				new String[] {
						"?M?",
						"?C?",
						"???" },
				new String[] {
						"?H?",
						"?r?",
						"???" });

		/** Acid with wood at its left: the wood becomes smoke and the drop pays a bite. */
		private static final Step ACID_CORRODES_LEFT_STEP = new Step(
				new String[] {
						"???",
						"MC?",
						"???" },
				new String[] {
						"???",
						"Hr?",
						"???" });

		/** Acid with wood at its right: the wood becomes smoke and the drop pays a bite. */
		private static final Step ACID_CORRODES_RIGHT_STEP = new Step(
				new String[] {
						"???",
						"?CM",
						"???" },
				new String[] {
						"???",
						"?rH",
						"???" });

		/**
		 * An acid that has another acid just below it rolls down to a diagonal when
		 * there is water there: the two change places and the drop of the water comes
		 * up to the cell the acid leaves, so a column of acid inside a pond spreads
		 * instead of standing on end.
		 * <p>
		 * It is the same roll as the grain of sand and for the same reason: the
		 * levelling of the acid asks for free space at its sides, and inside the
		 * water what it has is water, so a pool of acid that reached the bottom of a
		 * pond stayed in the shape it fell in.
		 * </p>
		 * <p>
		 * It goes after the corroding rules, because a drop that can bite has to bite
		 * before it moves, and before the diluting ones, because a drop that still
		 * has somewhere to go goes there before the water starts eating it. The
		 * dilution claims the cell of the drop even on the turns its age is not due,
		 * so with the other order a drop surrounded by water was still every turn
		 * and a pool of acid could never spread.
		 * </p>
		 */
		private static final Step ACID_ROLLS_SIDEWAYS_IN_WATER_STEP = new Step(
				new String[] {
						"???",
						"?C?",
						"ACA" },
				new String[] {
						"???",
						"???",
						"p?p" });

		/** The same roll when only the left-down diagonal is water. */
		private static final Step ACID_ROLLS_LEFT_IN_WATER_STEP = new Step(
				new String[] {
						"???",
						"?C?",
						"ACX" },
				new String[] {
						"???",
						"???",
						"p??" });

		/** The same roll when only the right-down diagonal is water. */
		private static final Step ACID_ROLLS_RIGHT_IN_WATER_STEP = new Step(
				new String[] {
						"???",
						"?C?",
						"XCA" },
				new String[] {
						"???",
						"???",
						"??p" });

		/**
		 * An acid piled on more acid slides to the side when it has water at both
		 * sides: the drop takes the place of one of the two drops of water and the
		 * one it displaces takes the cell it leaves, so a pool of acid spreads flat
		 * instead of standing in a cone.
		 * <p>
		 * The rolls above move the drop one row down and one to a side, which is
		 * what a grain of sand does, and a heap of them keeps that shape: the
		 * diagonal below the edge of a row is the acid of the row underneath, so
		 * the roll never finds the water it needs and the slope of the cone stays
		 * at forty-five degrees in every layer. A liquid has no such slope. Its
		 * levelling, the one the water does in the air, is written with {@code x},
		 * which only writes where there is free space; inside a pond what the drop
		 * has at its sides is water, so that rule never matches and a drop of acid
		 * with acid below it and nothing to bite could only stay where it was.
		 * </p>
		 * <p>
		 * So this is an exchange and not a write: the drop takes the place of the
		 * water at its side and that water takes the cell the drop leaves. The drop
		 * stays in its own row, which is not a climb, and the count of drops does
		 * not move. When it slides out of the footprint of the row underneath, what
		 * it has below is water and the sinking of the drops takes it down, so the
		 * top of the pool goes down by its sides and the surface flattens; the drop
		 * of water it leaves behind is the hole it has just vacated.
		 * </p>
		 * <p>
		 * It asks for acid below, so it is the levelling of a pile and not a way of
		 * walking in the water: a drop with water below it goes down with the
		 * sinking rules, which come first, and the last row of a pool does not
		 * match, because what it has below is the floor of the pond. That is what
		 * keeps the bottom of the pool from sliding away.
		 * </p>
		 * <p>
		 * The exchange can point at its own row, which the pass may have read
		 * already: the swap asks for the neighbouring drop to still be written
		 * there, so a drop that flowed away in this very step is not swapped with a
		 * second time (see {@link #exchange}).
		 * </p>
		 * <p>
		 * It goes with the rolls and before the diluting rules, for the same reason
		 * as them: a drop that still has somewhere to go goes there before the
		 * water starts eating it.
		 * </p>
		 */
		private static final Step ACID_LEVELS_SIDEWAYS_IN_WATER_STEP = new Step(
				new String[] {
						"???",
						"ACA",
						"?C?" },
				new String[] {
						"???",
						"p?p",
						"???" });

		/** The same levelling when only the left side is water. */
		private static final Step ACID_LEVELS_LEFT_IN_WATER_STEP = new Step(
				new String[] {
						"???",
						"ACX",
						"?C?" },
				new String[] {
						"???",
						"p??",
						"???" });

		/** The same levelling when only the right side is water. */
		private static final Step ACID_LEVELS_RIGHT_IN_WATER_STEP = new Step(
				new String[] {
						"???",
						"XCA",
						"?C?" },
				new String[] {
						"???",
						"??p",
						"???" });

		/**
		 * An acid that has spent its charge becomes water: the drop reaches the last
		 * value of its band by eating wood or by being eaten by water, and in both
		 * cases what is left is a drop of water in the cell it occupied.
		 * <p>
		 * The water is spawned, so it is born with one of the shades a drop is born
		 * with, and the water that was doing the eating keeps its own value: the rule
		 * writes only the centre, and a liquid product written over the neighbour would
		 * start it again and reset the wait of a grain resting on it.
		 * </p>
		 * <p>
		 * It has to go before the diluting and the corroding rules, because both walk
		 * the centre with a glyph that cannot pass the end of the band: a spent drop
		 * next to water would wait for an age that can no longer change it, and a spent
		 * drop next to wood would bite without paying. Going first, the last value is
		 * always a water and never a value that has run out of its band.
		 * </p>
		 * <p>
		 * It is not one of the ends that the group of {@code FIRE_END} and
		 * {@code SMOKE_END} holds: a spent acid does not die, it is neutralised, and
		 * what is left is a liquid that the rules of the water take over.
		 * </p>
		 */
		private static final Step ACID_END_STEP = new Step(
				new String[] { "c" },
				new String[] { "A" });

		/**
		 * The same sinking, spread over the three cells below: with water on all of
		 * them the drop goes down to one of the three, drawn at equal parts, so a pour
		 * that crosses a pond does not cut it in a column.
		 * <p>
		 * It is the drawing the grain of sand uses to sink in water, and the reasons
		 * are the same: the three cells of the row below have not been read yet, which
		 * is the only direction a swap can be written in, and {@code p} shares the
		 * change, so one of the three takes the drop and only one drop of water comes
		 * up.
		 * </p>
		 * <p>
		 * When the side cells are not water the rule does not match and the drop goes
		 * straight down, which is what keeps an acid that falls into a pond through
		 * its surface from leaving it sideways the moment it touches the water.
		 * </p>
		 */
		private static final Step ACID_SINKS_SIDEWAYS_IN_WATER_STEP = new Step(
				new String[] {
						"???",
						"?C?",
						"AAA" },
				new String[] {
						"???",
						"???",
						"ppp" });

		/**
		 * Acid heavier than water sinks in it: the drop changes places with the water
		 * directly below, so a pour reaches the bottom of a pond, which is where the
		 * wood is.
		 * <p>
		 * It goes before the diluting rules, and that is what makes the two work
		 * together: a drop that touches water can either go down or be eaten, and it
		 * goes down. Only the drop that has no water below and no wood to bite stays
		 * still long enough to be eaten, so the charge is spent where it is useful
		 * and not on the way.
		 * </p>
		 * <p>
		 * It comes after the sideways one, which is the same sinking drawn over the
		 * three cells below: when all three are water the drop goes to one of them,
		 * and straight down only when the side cells are not.
		 * </p>
		 * <p>
		 * The water comes up into the cell the acid leaves, and the exchange is
		 * written whole by the rule, the same way as the grain that sinks in acid.
		 * The cell below is one the sweep has not read yet, which is the only
		 * direction a swap can be written in.
		 * </p>
		 */
		private static final Step ACID_SINKS_IN_WATER_STEP = new Step(
				new String[] {
						"???",
						"?C?",
						"?A?" },
				new String[] {
						"???",
						"???",
						"?P?" });

		/**
		 * Water at any of the four sides eats a value of the acid: the drop pays a
		 * value of its charge and the water is left as it is.
		 * <p>
		 * This is what makes the neutralisation a process. {@code W} advances the band
		 * only when the age gives it the turn, so the drop loses one value every
		 * {@code ages} turns, and a drop that has just met water is five values away
		 * from becoming water. The front of the dilution therefore advances one cell
		 * every six hundred steps instead of one cell per step, which is what a puddle
		 * needs to survive not one drop but the whole session.
		 * </p>
		 * <p>
		 * They have to go after {@code ACID_END}, because a {@code C} in the centre
		 * also matches the {@code c} of a spent drop: with the other order that drop
		 * would wait for an age that can no longer change it and would never become
		 * water. And they go after the corroding rules, so an acid that reaches the
		 * wood at the bottom of a pond bites it instead of dissolving into the water
		 * above.
		 * </p>
		 */

		/** Water above eats the acid: the drop pays a value of its charge. */
		private static final Step ACID_DILUTES_ABOVE_STEP = new Step(
				new String[] {
						"?A?",
						"?C?",
						"???" },
				new String[] {
						"???",
						"?W?",
						"???" });

		/** Water below eats the acid: the drop pays a value of its charge. */
		private static final Step ACID_DILUTES_BELOW_STEP = new Step(
				new String[] {
						"???",
						"?C?",
						"?A?" },
				new String[] {
						"???",
						"?W?",
						"???" });

		/** Water at its left eats the acid: the drop pays a value of its charge. */
		private static final Step ACID_DILUTES_LEFT_STEP = new Step(
				new String[] {
						"???",
						"AC?",
						"???" },
				new String[] {
						"???",
						"?W?",
						"???" });

		/** Water at its right eats the acid: the drop pays a value of its charge. */
		private static final Step ACID_DILUTES_RIGHT_STEP = new Step(
				new String[] {
						"???",
						"?CA",
						"???" },
				new String[] {
						"???",
						"?W?",
						"???" });

		/**
		 * Acid with free space at both sides flows to one of them, exactly like the
		 * water: the falling, the diagonal slide and the stacking are inherited from
		 * the generic gravity, so levelling is the only liquidity the acid has to be
		 * told.
		 */
		private static final Step ACID_FLOWS_BOTH_STEP = new Step(
				new String[] {
						"???",
						"_C_",
						"?X?" },
				new String[] {
						"???",
						"x_x",
						"???" });

		/** Acid with free space only at its left flows there. */
		private static final Step ACID_FLOWS_LEFT_STEP = new Step(
				new String[] {
						"???",
						"_CX",
						"?X?" },
				new String[] {
						"???",
						"X_?",
						"???" });

		/** Acid with free space only at its right flows there. */
		private static final Step ACID_FLOWS_RIGHT_STEP = new Step(
				new String[] {
						"???",
						"XC_",
						"?X?" },
				new String[] {
						"???",
						"?_X",
						"???" });

		/**
		 * A grain over acid changes place with the drop instead of resting on it.
		 * <p>
		 * The water holds a grain because the band of the drop is the wait of the
		 * grain; the acid has no such band to spare, nor would it be right to spend
		 * the acid on a grain of sand. So the grain does what its density says: it
		 * goes down, and the drop it displaces leaves by the cell the grain vacated
		 * or by a free side of it. One row per turn of the grain, the same as
		 * falling in the air, which is what makes the acid a liquid the sand sinks
		 * in and not a floor.
		 * </p>
		 */
		private static final Step SAND_FALLS_THROUGH_ACID_STEP = new Step(
				new String[] {
						"???",
						"?S?",
						"?C?" },
				new String[] {
						"???",
						"???",
						"?P?" });

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
		 * The rules of the slow sink go together and before the free fall: a grain
		 * is not held by a mark of its own but by the drop it rests on, which is
		 * still water, and the band of that drop is the wait. The order between
		 * them matters. The wait goes before the forget, because the forget
		 * matches any drop at the end of its band and would start again the one
		 * that still has a grain on top. And the exchange goes last of the group
		 * and comes in two forms: the grain changes place with one of the three
		 * drops under it, and when the diagonals are not water it changes place
		 * with the one below, so a grain is never held for ever by a drop that has
		 * already finished its wait.
		 * </p>
		 * <p>
		 * The acid appears in two places. Its end goes at the top, with the quench,
		 * because a spent drop has to become water before the generic gravity or any
		 * of its own rules can move it. Everything else goes after the free fall, in
		 * the order of the drop's life: it sinks through the water it finds below,
		 * then bites the wood it lands on, then rolls to a diagonal when what it has
		 * below is more acid, then slides to a side when what it has there is water
		 * and it is sitting on that same acid, then lets itself be diluted, and only
		 * then levels with the free slides like any other liquid. The sand that falls
		 * through the acid goes with the other sinkings, because it is the same idea,
		 * a grain changing place with the liquid instead of falling at it; and the
		 * sand that lands on sand rolls like the acid, with the same three drawings.
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
				ACID_END_STEP,
				FIRE_END_STEP,
				SMOKE_END_STEP,
				WOOD_END_STEP,
				WOOD_BURNS_ABOVE_STEP,
				WOOD_BURNS_BELOW_STEP,
				WOOD_BURNS_LEFT_STEP,
				WOOD_BURNS_RIGHT_STEP,
				FIRE_FEEDS_STEP,
				FIRE_FEEDS_ABOVE_STEP,
				WOOD_STAYS_STEP,
				FIRE_RISES_STEP,
				FIRE_SPREADS_STEP,
				SMOKE_RISES_STEP,
				SMOKE_AGES_STEP,
				WATER_PUTS_OUT_ABOVE_STEP,
				WATER_PUTS_OUT_UPPER_LEFT_STEP,
				WATER_PUTS_OUT_UPPER_RIGHT_STEP,
				WATER_WAITS_UNDER_SAND_STEP,
				WATER_FORGETS_THE_WAIT_STEP,
				SAND_SINKS_SIDEWAYS_INTO_WATER_STEP,
				SAND_SINKS_STRAIGHT_INTO_WATER_STEP,
				SAND_ROLLS_SIDEWAYS_IN_WATER_STEP,
				SAND_ROLLS_LEFT_IN_WATER_STEP,
				SAND_ROLLS_RIGHT_IN_WATER_STEP,
				SAND_FALLS_THROUGH_ACID_STEP,
				FREE_FALL_STEP,
				ACID_SINKS_SIDEWAYS_IN_WATER_STEP,
				ACID_SINKS_IN_WATER_STEP,
				ACID_CORRODES_BELOW_STEP,
				ACID_CORRODES_ABOVE_STEP,
				ACID_CORRODES_LEFT_STEP,
				ACID_CORRODES_RIGHT_STEP,
				ACID_ROLLS_SIDEWAYS_IN_WATER_STEP,
				ACID_ROLLS_LEFT_IN_WATER_STEP,
				ACID_ROLLS_RIGHT_IN_WATER_STEP,
				ACID_LEVELS_SIDEWAYS_IN_WATER_STEP,
				ACID_LEVELS_LEFT_IN_WATER_STEP,
				ACID_LEVELS_RIGHT_IN_WATER_STEP,
				ACID_DILUTES_ABOVE_STEP,
				ACID_DILUTES_BELOW_STEP,
				ACID_DILUTES_LEFT_STEP,
				ACID_DILUTES_RIGHT_STEP,
				ACID_FLOWS_BOTH_STEP,
				ACID_FLOWS_LEFT_STEP,
				ACID_FLOWS_RIGHT_STEP,
				WATER_FLOWS_BOTH_STEP,
				WATER_FLOWS_LEFT_STEP,
				WATER_FLOWS_RIGHT_STEP,
				SLIDE_BOTH_STEP,
				SLIDE_LEFT_STEP,
				SLIDE_RIGHT_STEP,
				STAY_STEP };
	}

}
