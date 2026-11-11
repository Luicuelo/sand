package principal;

import javafx.scene.paint.Color;

/**
 * A material: one glyph, one band of palette values and the colours of that
 * band.
 * <p>
 * A cell holds a palette index, so material and state travel together: the
 * position inside the band is the life of the particle, and the {@code V},
 * {@code W}, {@code r} and {@code l} glyphs walk it. The bands are laid out
 * from index 1 in the order of {@link #LIST}, so configuring a material is
 * writing its entry in that list: glyph, size, clock and colours.
 * </p>
 * <p>
 * Each material also carries its own clock: how many steps of the world pass
 * between two evaluations of one of its particles. A grain of sand is looked at
 * one step in six and a drop of water in every step, so the liquid falls and
 * spreads six times faster than the grain. The clock is not the speed of the
 * world: the renderer takes several steps per frame, and that is what makes
 * everything faster or slower at once. What the clocks have to keep is the
 * alternation of the sweep, which swaps sides every frame: the question is in
 * which frames the turns of a material land, because one whose turns always land
 * on the same side reads its rows in the same direction every time and pushes its
 * pile sideways. A grain is due one step in six and a frame has four, so its
 * turns fall in frames 0, 1, 3, 4, 6... and see both sides; an even quotient
 * between clock and steps per frame would keep it on one side.
 * </p>
 * <p>
 * {@link #glyphOf} holds the same information the other way round: from the
 * value of a cell to the glyph that a match has to ask for.
 * </p>
 */
class Material {
	/** Glyph of an empty cell, in a match and in a result. */
	static final char EMPTY = '_';
	/** Glyph that matches anything in a match, and writes nothing in a result. */
	static final char ANY = '?';
	/** Glyph of the particle itself: any material in a match, its own value in a result. */
	static final char PARTICLE = 'X';

	static final int PALETTE_SIZE = 256;

	/** First free palette index, and the one after the last band once built. */
	private static int next = 1;

	/**
	 * Materials in the order of their bands, which are laid out from index 1.
	 * <p>
	 * The size of a band is the number of values the material owns, and for a
	 * material with life it is also how long it lasts: one value per step. The
	 * spread is how many values, counted from the first of the band, a new
	 * particle may take: the wider it is, the less the grains look like a flat
	 * sheet, and the more life a particle of a material with life loses at
	 * birth. The number after the spread is the clock: how many steps of the
	 * world pass between two evaluations of one particle. The colours are the
	 * stops the band is interpolated through.
	 * </p>
	 * <p>
	 * The clock is what makes the difference between a grain and a drop: the
	 * sand is looked at one step in six and the water in every one, so the
	 * water falls six times faster and reaches the bottom of the screen
	 * first. The materials with life stay at three, so their lives are still
	 * counted in steps and the fire still climbs a third of the screen, at the
	 * pace it had before the world was sped up. The turns of a material fall one
	 * every clock steps, so an even quotient between its clock and the steps per
	 * frame would keep every one of them on the same side of the sweep.
	 * </p>
	 */
	static final Material[] LIST = {
			new Material('S', 25, false, 25, 6, "#f7e8a4", "#e6cd66", "#b39a3a"),
			new Material('A', 20, false, 20, 1, "#9ed7f7", "#4c9bd6", "#1b5188"),
			new Material('M', 30, true, 9, 3, "#d8b478", "#a1713c", "#5d3d20"),
			new Material('F', 30, true, 6, 3, "#fff6b0", "#ffb020", "#cc2a08"),
			new Material('H', 80, true, 6, 3, "#8c8c8c", "#bdbdbd", "#e2e2e2"),
			// The water that is holding a grain of sand on top of it, waiting to
			// swap places with it. The band is the wait and not a life: the walk
			// from the first value to the last is the turns the grain is held,
			// so the band is short and the colours are almost the ones of water,
			// and it is not painted by the brush because nobody puts it there
			// but a rule
			new Material('N', 6, false, 1, 6, false, "#8fcdf2", "#4591cd", "#1a4c80") };

	/** Glyph of the material that owns each palette value (0: empty). */
	static final char[] glyphOf = new char[PALETTE_SIZE];

	/**
	 * Steps between two evaluations of the material that owns each palette
	 * value. Empty cells get 1, a clock that never divides by zero.
	 */
	private static final int[] stepPeriodOf = new int[PALETTE_SIZE];

	static {
		glyphOf[0] = EMPTY;
		stepPeriodOf[0] = 1;
		for (Material material : LIST)
			for (int value = material.first; value <= material.last(); value++) {
				glyphOf[value] = material.glyph;
				stepPeriodOf[value] = material.stepPeriod;
			}
		if (next > PALETTE_SIZE - 1)
			throw new IllegalStateException("The material bands need " + (next - 1) + " values and the palette has "
					+ (PALETTE_SIZE - 1));
	}

	final char glyph;
	/** Number of palette values of the band, that is, how long the life lasts. */
	final int size;
	/** True when the band is a life counter, so its position is the age. */
	final boolean life;
	/** Values, counted from the first of the band, that a new particle may take. */
	final int spread;
	/**
	 * Steps of the world that pass between two evaluations of one of its
	 * particles.
	 */
	final int stepPeriod;
	/**
	 * True when the brush can put the material in the world. A state that only
	 * a rule writes, such as the water that is holding a grain, is not offered
	 * in the selector.
	 */
	final boolean paintable;
	/** Colours the band is interpolated through. */
	final Color[] stops;
	/** First palette value of the band. */
	final int first;

	private Material(char glyph, int size, boolean life, int spread, int stepPeriod, String... stops) {
		this(glyph, size, life, spread, stepPeriod, true, stops);
	}

	private Material(char glyph, int size, boolean life, int spread, int stepPeriod, boolean paintable,
			String... stops) {
		this.glyph = glyph;
		this.size = size;
		this.life = life;
		this.spread = Math.max(1, Math.min(spread, size));
		this.stepPeriod = Math.max(1, stepPeriod);
		this.paintable = paintable;
		this.stops = new Color[stops.length];
		for (int i = 0; i < stops.length; i++)
			this.stops[i] = Color.web(stops[i]);
		this.first = next;
		next += size;
	}

	/** Last palette value of the band. */
	int last() {
		return first + size - 1;
	}

	/** Whether a palette value belongs to this band. */
	boolean contains(int value) {
		return value >= first && value <= last();
	}

	/**
	 * Palette value of a particle created by a rule.
	 * <p>
	 * The particle takes a value at random among the first ones of its band
	 * ({@link #spread}), so the grains of a pile are not all the same colour. In
	 * a material without life that is all it means. In a material with life the
	 * value is the age, so the spread is also how much life a particle can lose
	 * at birth: it is kept short on purpose, or a flame painted on the floor
	 * would not have enough steps left to reach the ceiling.
	 * </p>
	 *
	 * @return the palette value of the new particle
	 */
	int spawn() {
		return first + (int) (Math.random() * spread);
	}

	/**
	 * Colour of one value of the band, interpolating the stops.
	 *
	 * @param index position inside the band, from 0 to {@code size - 1}
	 * @return the colour of that position
	 */
	Color color(int index) {
		double scaled = size <= 1 ? 0 : index / (double) (size - 1) * (stops.length - 1);
		int segment = Math.min((int) scaled, stops.length - 2);
		return stops[segment].interpolate(stops[segment + 1], scaled - segment);
	}

	/**
	 * Colour that represents the whole band, the middle one.
	 * <p>
	 * The ends of a band are its brightest and its darkest value, so the middle
	 * is the one that reads as the material in a small control such as the button
	 * of the selector.
	 * </p>
	 *
	 * @return the colour of the middle value of the band
	 */
	Color tint() {
		return color(size / 2);
	}

	/**
	 * Material of a glyph, in uppercase.
	 *
	 * @param glyph the glyph to look for
	 * @return the material, or null when no material uses that glyph
	 */
	static Material byGlyph(char glyph) {
		for (Material material : LIST)
			if (material.glyph == glyph)
				return material;
		return null;
	}

	/**
	 * Whether the material of a palette value has its turn in this step.
	 * <p>
	 * The renderer counts steps of the world and every material divides that
	 * count by its clock: a period of one means a step in every step, a period
	 * of six means one step in six. The step a particle does not get is not a
	 * pause: the cell is copied to the next generation as it is, so the particle
	 * keeps its place, its colour and its life until its clock strikes again.
	 * </p>
	 *
	 * @param value the palette value of a particle
	 * @param step  the step of the world, counted from the start
	 * @return true when the particle of that value has to be stepped now
	 */
	static boolean isDue(int value, int step) {
		return step % stepPeriodOf[value] == 0;
	}

	/**
	 * Material that owns a palette value.
	 *
	 * @param value the palette value to look for
	 * @return the material, or null when the value is empty or unused
	 */
	static Material ofValue(int value) {
		for (Material material : LIST)
			if (material.contains(value))
				return material;
		return null;
	}

	/**
	 * {@code V}: one step towards the end of the band, and back to the first value
	 * when it reaches the end.
	 *
	 * @param value the palette value to advance
	 * @return the next value, or the value itself when it belongs to no material
	 */
	static int advanceWrapped(int value) {
		Material material = ofValue(value);
		if (material == null)
			return value;
		return value < material.last() ? value + 1 : material.first;
	}

	/**
	 * {@code W}: one step towards the end of the band, staying at the last value
	 * when it is already there. Running out of life is a rule of its own, so this
	 * one only moves the particle to the end.
	 *
	 * @param value the palette value to advance
	 * @return the next value, or the value itself when it belongs to no material
	 */
	static int advanceClamped(int value) {
		Material material = ofValue(value);
		if (material == null || value >= material.last())
			return value;
		return value + 1;
	}

	/**
	 * {@code r}: half of the life that is left, but at least one step, so a value
	 * one step before the end does not get stuck.
	 *
	 * @param value the palette value that loses life
	 * @return the new value, at most the last of the band
	 */
	static int burn(int value) {
		Material material = ofValue(value);
		if (material == null)
			return value;
		int left = material.last() - value;
		if (left <= 0)
			return value;
		return Math.min(material.last(), value + Math.max(1, left / 2));
	}

	/**
	 * {@code l}: half of the life already spent, but at least one step, so a value
	 * one step after the start does not get stuck.
	 *
	 * @param value the palette value that recovers life
	 * @return the new value, at least the first of the band
	 */
	static int feed(int value) {
		Material material = ofValue(value);
		if (material == null)
			return value;
		int used = value - material.first;
		if (used <= 0)
			return value;
		return Math.max(material.first, value - Math.max(1, used / 2));
	}
}
