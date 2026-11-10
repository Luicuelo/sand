package principal;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Renderer for the falling sand particle simulation.
 * <p>
 * This class handles the visualization of the particle simulation, including
 * rendering particles to the screen, managing the color palette, and processing
 * user interactions. It uses a block-based rendering approach where each cell
 * in the simulation is rendered as a PIXEL_SIZE x PIXEL_SIZE pixel block.
 * </p>
 * <p>
 * The renderer maintains two buffers for double buffering - one for the current
 * state and one for the next state. It also manages the palette of colors used
 * for rendering different particle types.
 * </p>
 * 
 * @see Sand
 * @see Transition
 */
class Renderer {
	private static final int PALETTE_SIZE = Material.PALETTE_SIZE;
	private static final int PIXEL_SIZE = 8; // block of PIXEL_SIZE x PIXEL_SIZE pixels per cell
	private static final int BYTES_PER_PIXEL = 4; // BGRA
	private static final int DROP_EVERY_FRAMES = 2; // drop particles only every other frame (30 times/s)
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
	private final Transition transition;
	private final byte[] frameBuffer; // BGRA pixels of the whole canvas
	private int[][] cells; // generation buffer
	private int[][] nextCells; // next generation buffer
	private int mouseX = 0;
	private int mouseY = 0;
	private boolean mouseInside = false;
	private int frameCounter = 0;
	private final Palette palette = new Palette();
	private Material selected = Material.LIST[0];
	private int hitDirection = 0; // 0 = no hit, otherwise Transition.LEFT or Transition.RIGHT
	private int hitForce = 0; // strength of the hit (1 to Transition.MAX_HIT_FORCE)
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
	public Renderer(GraphicsContext gc, int w, int h) {
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
		transition = new Transition(cols, rows);

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
	 * Advances the simulation by one frame (called at 60 FPS): adds particles
	 * under the mouse, runs one simulation step and redraws the canvas.
	 */
	public void tick() {
		// Drop a particle under the mouse only every DROP_EVERY_FRAMES frames
		if (mouseInside && frameCounter % DROP_EVERY_FRAMES == 0) {
			int cellX = mouseX / PIXEL_SIZE;
			int cellY = mouseY / PIXEL_SIZE;
			if (spacePressed) {
				for (int dy = -BRUSH_RADIUS; dy <= BRUSH_RADIUS; dy++) {
					for (int dx = -BRUSH_RADIUS; dx <= BRUSH_RADIUS; dx++) {
						int cx = cellX + dx;
						int cy = cellY + dy;
						if (cx >= 0 && cx < cols && cy >= 0 && cy < rows && cells[cy][cx] == 0) {
							cells[cy][cx] = selected.spawn();
						}
					}
				}
			} else {
				if (cellX >= 0 && cellX < cols && cellY >= 0 && cellY < rows && cells[cellY][cellX] == 0) {
					cells[cellY][cellX] = selected.spawn();
				}
			}
		}
		frameCounter++;
		stepSimulation();
		gc.drawImage(frameImage, 0, 0);
	}

	/**
	 * Performs one step of the simulation.
	 * <p>
	 * This method updates the state of all particles in the simulation based on
	 * the physics rules defined in the Transition class. It handles both regular
	 * particle movement and hit forces applied by the user.
	 * </p>
	 * <p>
	 * When it returns, {@code cells} holds the new generation and {@code nextCells}
	 * is empty, ready to be written by the next step.
	 * </p>
	 */
	private void stepSimulation() {
		if (hitForce > 0) {
			pushParticles();
			// The hit result becomes the current state; gravity is applied on top of it
			advanceGeneration();
		}

		// Gravity: empty cells cannot move anything, so they are skipped.
		// Every row is walked from side to side, and the side is swapped every
		// generation: when two particles want the same cell the one read first
		// keeps it, so a fixed side would always favour the same particle and a
		// growing pile would lean towards it
		boolean rightToLeft = (frameCounter & 1) == 0;
		for (int y = 0; y < rows; y++) {
			for (int i = 0; i < cols; i++) {
				int x = rightToLeft ? cols - 1 - i : i;
				if (cells[y][x] != 0) {
					transition.stepParticle(x, y, cells, nextCells);
				}
			}
		}

		// A hit only lives HIT_FRAMES frames
		if (hitForce > 0) {
			hitCount++;
		}
		if (hitCount > HIT_FRAMES) {
			hitForce = 0;
			hitDirection = 0;
			hitCount = 0;
		}

		advanceGeneration();
		renderFrame();
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
		boolean pushRight = hitDirection == Transition.RIGHT;
		for (int i = 0; i < cols; i++) {
			int x = pushRight ? cols - 1 - i : i;
			for (int y = 0; y < rows; y++) {
				if (cells[y][x] != 0) {
					transition.pushParticle(x, y, cells, nextCells, hitForce, hitDirection);
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
	 * {@link Transition#MAX_HIT_FORCE}.
	 * </p>
	 *
	 * @param button true if the primary (left) button was pressed, false if secondary (right)
	 */
	public void applyHit(boolean button) {
		hitDirection = button ? Transition.LEFT : Transition.RIGHT;
		hitForce = (int) (Math.random() * Transition.MAX_HIT_FORCE) + 1;
		hitCount = 0; // a new hit starts its own countdown
	}
}
