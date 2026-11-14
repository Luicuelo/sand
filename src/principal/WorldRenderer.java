package principal;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * World, clock and picture of the materials simulator.
 * <p>
 * This class handles the simulation and its visualization, including stepping
 * the particles, rendering them to the screen, managing the color palette, and
 * processing user interactions. It uses a block-based rendering approach where
 * each cell in the simulation is rendered as a PIXEL_SIZE x PIXEL_SIZE pixel
 * block.
 * </p>
 * <p>
 * The renderer keeps two clocks. The frame counter counts pictures, and the hit
 * and the drawing divide it. The step counter counts turns of the world, and
 * every material divides that one: each material carries its own period, so a
 * drop of water is stepped in every step and a grain of sand in one out of
 * twelve. The world takes {@link #ENGINE_MULTIPLIER} steps per frame, which is
 * how fast the whole world runs, while the period is how slow one material is
 * next to the others. The picture is drawn once every {@link #FRAMES_PER_PAINT}
 * frames, which is all the eye needs and saves the rest of the redraws.
 * </p>
 * <p>
 * The renderer maintains two buffers for double buffering - one for the current
 * state and one for the next state. It also manages the palette of colors used
 * for rendering different particle types.
 * </p>
 * 
 * @see MaterialSimulator
 * @see MaterialRules
 */
class WorldRenderer {
	private static final int PALETTE_SIZE = Material.PALETTE_SIZE;
	private static final int PIXEL_SIZE = 8; // block of PIXEL_SIZE x PIXEL_SIZE pixels per cell
	private static final int BYTES_PER_PIXEL = 4; // BGRA
	private static final int ENGINE_MULTIPLIER = 9; // steps of the world per frame
	private static final int FRAMES_PER_PAINT = 1; // frames between two drawn pictures
	private static final int BRUSH_RADIUS = 2; // cells painted around the mouse while SPACE is held
	private static final int HIT_FRAMES = 10; // frames a hit force stays active
	private static final WritablePixelFormat<ByteBuffer> PIXEL_FORMAT = PixelFormat.getByteBgraInstance();

	private final GraphicsContext gc;
	private final int widthPx;
	private final int heightPx;
	private final int cols;
	private final int rows;
	private final WritableImage frameImage;
	private final PixelWriter pixelWriter;
	private final MaterialRules rules;
	private final byte[] frameBuffer; // BGRA pixels of the whole canvas
	private int[][] cells; // generation buffer
	private int[][] nextCells; // next generation buffer
	private int mouseX = 0;
	private int mouseY = 0;
	private boolean mouseInside = false;
	private int frameCounter = 0; // frames drawn: the clock of the hit and of the picture
	private int stepCounter = 0; // steps of the world: the clock every material divides
	private final Palette palette = new Palette();
	private Material selected = Material.LIST[0];
	private int hitDirection = 0; // 0 = no hit, otherwise MaterialRules.LEFT or MaterialRules.RIGHT
	private int hitForce = 0; // strength of the hit (1 to MaterialRules.MAX_HIT_FORCE)
	private int hitCount = 0;
	private boolean spacePressed = false;

	/**
	 * Internal palette class for managing the colors used in the simulation.
	 * <p>
	 * The palette contains 256 colors where:
	 * <ul>
	 *   <li>Index 0: white (background)</li>
	 *   <li>Every other index: the color of one value of a material, interpolating
	 *       the stops of its band. The last value of a material with life is a
	 *       very light tint, so a particle that has spent its life is almost
	 *       invisible while it waits for the rule that transforms it</li>
	 * </ul>
	 * </p>
	 */
	private static class Palette {
		final byte[][] colors = new byte[PALETTE_SIZE][4];

		/**
		 * Constructs a new palette from the material bands, with a white background
		 * and a color for every value of every material.
		 */
		public Palette() {
			for (int index = 0; index < PALETTE_SIZE; index++)
				setColor(index, javafx.scene.paint.Color.WHITE);

			for (Material material : Material.LIST) {
				for (int value = material.first; value <= material.last(); value++) {
					javafx.scene.paint.Color color = material.color(value - material.first);
					if (material.life && value == material.last())
						color = color.interpolate(javafx.scene.paint.Color.WHITE, 0.85);
					setColor(value, color);
				}
			}
		}

		/**
		 * Writes one color of the palette in the byte order of the frame buffer.
		 *
		 * @param index the palette index to write
		 * @param color the color of that index
		 */
		private void setColor(int index, javafx.scene.paint.Color color) {
			colors[index][0] = (byte) (int) (color.getBlue() * 255);
			colors[index][1] = (byte) (int) (color.getGreen() * 255);
			colors[index][2] = (byte) (int) (color.getRed() * 255);
			colors[index][3] = (byte) 255;
		}
	}

	/**
	 * Creates a new renderer and draws the initial (empty) frame.
	 *
	 * @param gc the graphics context to draw on
	 * @param w  canvas width in pixels
	 * @param h  canvas height in pixels
	 * @throws IllegalArgumentException if gc is null or dimensions are invalid
	 */
	public WorldRenderer(GraphicsContext gc, int w, int h) {
		if (gc == null) {
			throw new IllegalArgumentException("GraphicsContext cannot be null");
		}
		if (w <= 0 || h <= 0) {
			throw new IllegalArgumentException("Width and height must be positive");
		}

		this.gc = gc;
		this.widthPx = w;
		this.heightPx = h;
		this.cols = widthPx / PIXEL_SIZE;
		this.rows = heightPx / PIXEL_SIZE;

		// Image, pixel writer and the buffer holding a whole frame
		frameImage = new WritableImage(widthPx, heightPx);
		pixelWriter = frameImage.getPixelWriter();
		frameBuffer = new byte[widthPx * heightPx * BYTES_PER_PIXEL];
		cells = new int[rows][cols];
		nextCells = new int[rows][cols];
		rules = new MaterialRules(cols, rows);

		initialize();
	}

	/**
	 * Initializes the renderer by clearing the data arrays and drawing the initial image.
	 */
	private void initialize() {
		// Clear matrix (all cells to 0), reusing the existing buffers
		clearGrid(cells);
		clearGrid(nextCells);

		renderFrame();
		gc.drawImage(frameImage, 0, 0);
	}

	/**
	 * Advances the world by one frame (called at 60 FPS): steps the simulation
	 * {@link #ENGINE_MULTIPLIER} times and draws the picture when the paint clock
	 * marks it.
	 * <p>
	 * One call is one frame for the hit and for the picture, and
	 * {@link #ENGINE_MULTIPLIER} steps for the world and the brush. Raising the
	 * multiplier speeds everything up at once without touching a single rule:
	 * what changes from one material to another is how many steps it waits for
	 * its turn, and what changes from the world to the picture is that the
	 * picture is only redrawn one frame in {@link #FRAMES_PER_PAINT}.
	 * </p>
	 */
	public void tick() {
		if (hitForce > 0)
			applyHitForce();
		for (int step = 0; step < ENGINE_MULTIPLIER; step++)
			stepSimulation();
		if (frameCounter % FRAMES_PER_PAINT == 0) {
			renderFrame();
			gc.drawImage(frameImage, 0, 0);
		}
		frameCounter++;
	}

	/**
	 * Puts a particle of the selected material in the cell of the mouse, or in
	 * a square around it while SPACE is held.
	 * <p>
	 * The brush is part of the step, not of the picture: it runs inside
	 * {@link #stepSimulation()} and drips one particle every two turns of the
	 * selected material, so a material that moves one step in twelve is painted
	 * one step in twenty-four.
	 * </p>
	 * <p>
	 * Two turns and not one is what leaves the grain room to fall: painted on
	 * every turn, a grain is born on the cell the one before has just left, sees
	 * it taken by the previous generation, and slides sideways in the air. With
	 * a turn of its own in between it is always born over free space and the jet
	 * falls as a column.
	 * </p>
	 */
	private void dropUnderMouse() {
		if (!mouseInside)
			return;
		if (stepCounter % (selected.stepPeriod * 2) != 0)
			return;
		int cellX = mouseX / PIXEL_SIZE;
		int cellY = mouseY / PIXEL_SIZE;
		if (spacePressed) {
			for (int dy = -BRUSH_RADIUS; dy <= BRUSH_RADIUS; dy++)
				for (int dx = -BRUSH_RADIUS; dx <= BRUSH_RADIUS; dx++)
					drop(cellX + dx, cellY + dy);
		} else {
			drop(cellX, cellY);
		}
	}

	/**
	 * Puts a particle of the selected material in one cell, if the cell is
	 * inside the grid and empty.
	 *
	 * @param cellX column of the cell
	 * @param cellY row of the cell
	 */
	private void drop(int cellX, int cellY) {
		if (cellX >= 0 && cellX < cols && cellY >= 0 && cellY < rows && cells[cellY][cellX] == 0)
			cells[cellY][cellX] = selected.spawn();
	}

	/**
	 * Pushes every particle in the direction of the current hit, once per
	 * frame, and counts the frames the hit is still alive.
	 * <p>
	 * The hit is a gesture of the mouse, so it is measured in frames and not in
	 * steps: a click moves the particles the same distance whatever the clock of
	 * each material is.
	 * </p>
	 */
	private void applyHitForce() {
		pushParticles();
		// The hit result becomes the current state; the steps come on top of it
		advanceGeneration();
		hitCount++;
		if (hitCount > HIT_FRAMES) {
			hitForce = 0;
			hitDirection = 0;
			hitCount = 0;
		}
	}

	/**
	 * Performs one step of the simulation: the brush, then the sweep.
	 * <p>
	 * This method updates the state of all particles in the simulation based on
	 * the physics rules defined in the MaterialRules class. Only the particles
	 * whose clock marks this step are stepped; the rest are copied to the next
	 * generation untouched, which is how the water moves in every step and the
	 * sand in one out of twelve.
	 * </p>
	 * <p>
	 * The brush goes first so that the particle it drops belongs to this
	 * generation and falls in the same step it is born, like any other.
	 * </p>
	 * <p>
	 * When it returns, {@code cells} holds the new generation and {@code nextCells}
	 * is empty, ready to be written by the next step.
	 * </p>
	 */
	private void stepSimulation() {
		dropUnderMouse();
		// Empty cells cannot move anything, so they are skipped.
		// Every row is walked from side to side, and the side is swapped every
		// frame: when two particles want the same cell the one read first keeps
		// it, so a fixed side would always favour the same particle and a
		// growing pile would lean towards it. Per frame and not per step,
		// because the clock of a material is counted in steps: swapped per step,
		// a material of an even period would always land on the same side.
		// Swapped per frame, the turns of the sand (one step in twelve, nine
		// steps per frame) fall in frames 0, 1, 2, 4, 5, 6 and see both sides; an
		// even quotient between the clock and the multiplier would not
		boolean rightToLeft = (frameCounter & 1) == 0;
		for (int y = 0; y < rows; y++) {
			for (int i = 0; i < cols; i++) {
				stepCell(rightToLeft ? cols - 1 - i : i, y, rightToLeft);
			}
		}

		advanceGeneration();
		stepCounter++;
	}

	/**
	 * Steps one particle when its clock marks this step, and copies it to the
	 * next generation as it is when it is not its turn.
	 * <p>
	 * Copying it is not an extra: the pass writes into the other buffer, so a
	 * particle left out there would disappear. Copied as it is, it keeps its
	 * place, its colour and its life until its clock strikes again.
	 * </p>
	 * <p>
	 * A cell that already has something in the next generation has no turn: a
	 * particle is only ever written where there was none, so the cells the pass
	 * has not read yet are empty in it, and whatever is there was written by a
	 * rule that reached the cell first, either a particle falling into it or an
	 * exchange that wrote both halves. The cell is left as it is, with its clock
	 * or without it.
	 * </p>
	 *
	 * @param x           column of the cell
	 * @param y           row of the cell
	 * @param rightToLeft true when the pass is reading the rows from right to left,
	 *                    which the rules need in order to know which neighbours
	 *                    still have their turn ahead
	 */
	private void stepCell(int x, int y, boolean rightToLeft) {
		if (nextCells[y][x] != 0)
			return;
		int value = cells[y][x];
		if (value == 0)
			return;
		if (Material.isDue(value, stepCounter))
			rules.stepParticle(x, y, cells, nextCells, rightToLeft, Material.isAgingDue(value, stepCounter));
		else
			nextCells[y][x] = value;
	}

	/**
	 * Pushes every particle in the direction of the current hit.
	 * <p>
	 * The grid is walked starting from the side the particles are pushed to, so a
	 * particle always lands on a cell whose own particle has already moved away.
	 * In the opposite order the origin of the particle behind would be cleared on
	 * top of the particle that just moved in.
	 * </p>
	 */
	private void pushParticles() {
		boolean pushRight = hitDirection == MaterialRules.RIGHT;
		for (int i = 0; i < cols; i++) {
			int x = pushRight ? cols - 1 - i : i;
			for (int y = 0; y < rows; y++) {
				if (cells[y][x] != 0) {
					rules.pushParticle(x, y, cells, nextCells, hitForce, hitDirection);
				}
			}
		}
	}

	/**
	 * Paints the whole grid into the image.
	 * <p>
	 * Every cell is expanded to a PIXEL_SIZE x PIXEL_SIZE block inside one byte
	 * buffer, which is sent to the image in a single call at the end. The strip
	 * left over when the canvas is not a multiple of PIXEL_SIZE stays black.
	 * </p>
	 */
	private void renderFrame() {
		for (int cy = 0; cy < rows; cy++) {
			for (int cx = 0; cx < cols; cx++) {
				writeBlock(cx, cy, palette.colors[cells[cy][cx]]);
			}
		}
		pixelWriter.setPixels(0, 0, widthPx, heightPx, PIXEL_FORMAT, frameBuffer, 0, widthPx * BYTES_PER_PIXEL);
	}

	/**
	 * Paints one cell as a block of PIXEL_SIZE x PIXEL_SIZE pixels inside the
	 * frame buffer.
	 *
	 * @param cx    cell column
	 * @param cy    cell row
	 * @param color the four BGRA components of the color
	 */
	private void writeBlock(int cx, int cy, byte[] color) {
		int firstColumn = cx * PIXEL_SIZE * BYTES_PER_PIXEL;
		for (int py = 0; py < PIXEL_SIZE; py++) {
			int rowStart = (cy * PIXEL_SIZE + py) * widthPx * BYTES_PER_PIXEL + firstColumn;
			for (int px = 0; px < PIXEL_SIZE; px++) {
				writeColor(frameBuffer, rowStart + px * BYTES_PER_PIXEL, color);
			}
		}
	}

	/**
	 * Copies one BGRA color into a byte buffer.
	 * <p>
	 * Writes the four color components (B, G, R, A) starting at {@code pos},
	 * which is the layout expected by the BGRA pixel format used for rendering.
	 * </p>
	 *
	 * @param buffer destination buffer
	 * @param pos    index where the color starts
	 * @param color  the four BGRA components of the color
	 */
	private static void writeColor(byte[] buffer, int pos, byte[] color) {
		buffer[pos] = color[0]; // B
		buffer[pos + 1] = color[1]; // G
		buffer[pos + 2] = color[2]; // R
		buffer[pos + 3] = color[3]; // A
	}

	/**
	 * Makes the grid written by the last pass the current state and leaves the
	 * recycled one empty for the next pass.
	 * <p>
	 * {@code cells} and {@code nextCells} are two independent arrays: this only
	 * changes which array each field points to, never their contents. The buffer
	 * left over after the exchange holds the previous generation, so it is
	 * cleared here to look like a brand new one.
	 * </p>
	 */
	private void advanceGeneration() {
		int[][] temp = cells;
		cells = nextCells;
		nextCells = temp;
		clearGrid(nextCells);
	}

	/**
	 * Sets every cell of a grid to 0 (empty).
	 * <p>
	 * Clears the buffer in place so it can be reused for the next generation
	 * instead of allocating a new array on every frame.
	 * </p>
	 *
	 * @param grid the grid to clear
	 */
	private static void clearGrid(int[][] grid) {
		for (int[] row : grid) {
			Arrays.fill(row, 0);
		}
	}

	/**
	 * Updates the mouse position used to drop particles.
	 *
	 * @param x      mouse X coordinate in pixels
	 * @param y      mouse Y coordinate in pixels
	 * @param inside true if mouse is inside the canvas
	 */
	public void setMousePosition(int x, int y, boolean inside) {
		this.mouseX = x;
		this.mouseY = y;
		this.mouseInside = inside;
	}

	/**
	 * Enables or disables the brush that paints a wider area around the mouse.
	 *
	 * @param pressed true while the SPACE key is held down
	 */
	public void setSpacePressed(boolean pressed) {
		this.spacePressed = pressed;
	}

	/**
	 * Selects the material that the mouse paints.
	 * <p>
	 * The glyph is enough to name a material, so the key pressed and the button
	 * of the toolbar say the same thing. Anything that is not a material is
	 * ignored, so the rest of the keyboard keeps working.
	 * </p>
	 *
	 * @param glyph glyph of the material, in lowercase or uppercase
	 */
	public void selectMaterial(char glyph) {
		Material material = Material.byGlyph(Character.toUpperCase(glyph));
		if (material != null)
			selected = material;
	}

	/**
	 * Restarts the simulation without recreating the renderer.
	 * Reuses the existing image and buffers.
	 */
	public void restart() {
		initialize();
	}

	/**
	 * Applies a hit force to the particles in the simulation.
	 * <p>
	 * This method is called when the user clicks on the canvas. It determines
	 * the direction of the force based on which mouse button was pressed and
	 * generates a random force strength between 1 and
	 * {@link MaterialRules#MAX_HIT_FORCE}.
	 * </p>
	 *
	 * @param button true if the primary (left) button was pressed, false if secondary (right)
	 */
	public void applyHit(boolean button) {
		hitDirection = button ? MaterialRules.LEFT : MaterialRules.RIGHT;
		hitForce = (int) (Math.random() * MaterialRules.MAX_HIT_FORCE) + 1;
		hitCount = 0; // a new hit starts its own countdown
	}
}
