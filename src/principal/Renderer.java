package principal;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.*;
import java.nio.ByteBuffer;

/**
 * Renderer for the falling sand particle simulation.
 * <p>
 * This class handles the visualization of the particle simulation, including
 * rendering particles to the screen, managing the color palette, and processing
 * user interactions. It uses a block-based rendering approach where each cell
 * in the simulation is rendered as a 4x4 pixel block for better performance.
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
	private static final int PALETTE_SIZE = 256;
	private static final int PIXEL_SIZE = 8; // 4x4 pixels per cell

	private final GraphicsContext gc;
	private final int width;
	private final int height;
	private final int cellWidth;
	private final int cellHeight;
	// private final Color[] palette = new Color[PALETTE_SIZE];
	private final WritableImage estadosImage;
	private final PixelWriter pixelWriter;
	private final Transition transition;
	private int[][] cellData; // generation buffer
	private int[][] nextCellData; // Next generation buffer
	private int mouseX = 0;
	private int mouseY = 0;
	private boolean mouseInside = false;
	private int frameCounter = 0;
	private final Palette palette = new Palette();
	private final byte[] blockBuffer = new byte[PIXEL_SIZE * PIXEL_SIZE * 4]; // BGRA format for 4x4 block
	private int hitDirection = 0; // Direction of the hit: 1=right, -1=left, 0=no hit
	private int hitForce = 0; // String of the hit
    private int hitCount = 0;

	/**
	 * Internal palette class for managing the colors used in the simulation.
	 * <p>
	 * The palette contains 256 colors where:
	 * <ul>
	 *   <li>Index 0: White (background)</li>
	 *   <li>Indices 1-255: Random grayscale values</li>
	 * </ul>
	 * </p>
	 */
	private static class Palette {
		final byte[][] colors = new byte[PALETTE_SIZE][4];

		/**
		 * Constructs a new palette with a white background color and random
		 * grayscale values for the other colors.
		 */
		public Palette() {

			colors[0][0] = (byte) 255;
			colors[0][1] = (byte) 255;
			colors[0][2] = (byte) 255;
			colors[0][3] = (byte) 255;

			for (int a = 1; a < PALETTE_SIZE; a++) {
				colors[a][0] = (byte) (50 + (Math.random() * 100));
				colors[a][1] = (byte) (50 + (Math.random() * 100));
				colors[a][2] = (byte) (50 + (Math.random() * 100));
				colors[a][3] = (byte) 255;
			}

		}

	}

	/**
	 * Creates a new plasma renderer and generates the initial fractal pattern.
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
		this.width = w;
		this.height = h;
		this.cellWidth = width / PIXEL_SIZE;
		this.cellHeight = height / PIXEL_SIZE;

		// Create writable image and pixel writer for efficient drawing
		estadosImage = new WritableImage(width, height);
		pixelWriter = estadosImage.getPixelWriter();
		cellData = new int[cellHeight][cellWidth];
		nextCellData = new int[cellHeight][cellWidth];
		transition = new Transition(cellWidth, cellHeight);

		initialize();
	}

	/**
	 * Performs initial drawing operations for the simulation.
	 * <p>
	 * This method is called during initialization to set up the initial state
	 * of the simulation. Currently it just prints a message to the console.
	 * </p>
	 */
	public void initialDrawing() {

		// initialColors();
		System.out.println("Initial Drawing");
		Thread.yield();
	}

	/**
	 * Initializes the renderer by clearing the data arrays and drawing the initial image.
	 */
	private void initialize() {
		// Clear matrix (all cells to 0)
		cellData = new int[cellHeight][cellWidth];
		nextCellData = new int[cellHeight][cellWidth];

		initialDrawing();
		updateImageFromPalette();
		gc.drawImage(estadosImage, 0, 0);
		System.out.println("Image Drawn");
	}

	/**
	 * Updates for one frame (called at 60 FPS). Processes pixel and redraws the
	 * canvas.
	 */
	public void updateRegion() {
		// Create point from mouse only every two frames
		if (mouseInside && frameCounter % 2 == 0) {
			int cellX = mouseX / PIXEL_SIZE;
			int cellY = mouseY / PIXEL_SIZE;
			// Check boundaries before assignment
			if (cellX >= 0 && cellX < cellWidth && cellY >= 0 && cellY < cellHeight && cellData[cellY][cellX] == 0) {
				int actualColor = (frameCounter / 500) % 254 + 1;
				cellData[cellY][cellX] = actualColor;
			}
		}
		frameCounter++;
		// clear next
		nextCellData = new int[cellHeight][cellWidth];
		iteration();
		gc.drawImage(estadosImage, 0, 0);
	}

	/**
	 * Performs one iteration of the simulation.
	 * <p>
	 * This method updates the state of all particles in the simulation based on
	 * the physics rules defined in the Transition class. It handles both regular
	 * particle movement and hit forces applied by the user.
	 * </p>
	 */
	public void iteration() {
		int actualColor = (frameCounter / 500) % 254 + 1;

        if(hitForce > 0) {
            for (int x = 0; x < cellWidth; x++) {
                for (int y = 0; y < cellHeight; y++) {
                    if (cellData[y][x] != 0) {
                        transition.getNextHitState(x, y, cellData, nextCellData, hitForce, hitDirection);
                    }
                }
            }
            // Swap references
            int[][] temp = cellData;
            cellData = nextCellData;
            nextCellData = temp;
            nextCellData = new int[cellHeight][cellWidth];

        }
		for (int y = 0; y < cellHeight; y++) {
			for (int x = 0; x < cellWidth; x++) {
				transition.getNextState(x, y, cellData, nextCellData, actualColor);
			}
		}
		
		// hit stops
        if (hitForce>0) hitCount++;
        if (hitCount > 10){
		    hitForce = 0;
		    hitDirection = 0;
            hitCount=0;
        }

		// Swap references
		int[][] temp = cellData;
		cellData = nextCellData;
		nextCellData = temp;

		updateImageFromPalette();
	}

	/**
	 * Updates the image from the palette data.
	 * <p>
	 * This method converts the cell data into pixel data for display. Each cell
	 * is rendered as a 4x4 pixel block using the color from the palette. The
	 * method uses block-based rendering for better performance.
	 * </p>
	 */
	private void updateImageFromPalette() {
		WritablePixelFormat<ByteBuffer> pixelFormat = PixelFormat.getByteBgraInstance();

		for (int cy = 0; cy < cellHeight; cy++) {
			for (int cx = 0; cx < cellWidth; cx++) {

				// Fill the entire 4x4 block (16 pixels) with the same color
				int color = cellData[cy][cx];
				byte b = palette.colors[color][0]; // B
				byte g = palette.colors[color][1]; // G
				byte r = palette.colors[color][2]; // R
				byte a = palette.colors[color][3]; // A

				for (int i = 0; i < PIXEL_SIZE * PIXEL_SIZE; i++) {
					int pos = i * 4;
					blockBuffer[pos] = b; // B
					blockBuffer[pos + 1] = g; // G
					blockBuffer[pos + 2] = r; // R
					blockBuffer[pos + 3] = a; // A
				}

				// Write 4x4 block at once
				int screenX = cx * PIXEL_SIZE;
				int screenY = cy * PIXEL_SIZE;
				if (screenX + PIXEL_SIZE <= width && screenY + PIXEL_SIZE <= height) {
					pixelWriter.setPixels(screenX, screenY, PIXEL_SIZE, PIXEL_SIZE, pixelFormat, blockBuffer, 0,
							PIXEL_SIZE * 4);
				}
			}
		}
	}

	/**
	 * Updates mouse position for interactive plasma effects.
	 *
	 * @param x      mouse X coordinate
	 * @param y      mouse Y coordinate
	 * @param inside true if mouse is inside the canvas
	 */
	public void setMousePosition(int x, int y, boolean inside) {
		this.mouseX = x;
		this.mouseY = y;
		this.mouseInside = inside;
	}

	/**
	 * Regenerates the plasma fractal pattern without recreating the renderer.
	 * Reuses existing WritableImage and draws a new fractal pattern.
	 */
	public void restart() {
		initialize();
	}

	/**
	 * Applies a hit force to the particles in the simulation.
	 * <p>
	 * This method is called when the user clicks on the canvas. It determines
	 * the direction of the force based on which mouse button was pressed and
	 * generates a random force strength between 1 and 10.
	 * </p>
	 *
	 * @param button true if the primary (left) button was pressed, false if secondary (right)
	 */
	public void applyHit(boolean button) {
		hitDirection = button? -1 : 1; // Izquierda -> derecha, Derecha -> izquierda
		hitForce = (int)(Math.random() * 10) + 1; // Fuerza 1-10
	}

	/*
	 * private void copyPixelRegion(int px, int py, int size) { int offsetX = ((int)
	 * Math.floor((Math.random() * MAX_DISPLACEMENT)) - 1); int offsetY = ((int)
	 * Math.floor((Math.random() * MAX_DISPLACEMENT)) - 1); if (px > 0 && py > 0 &&
	 * px + size - 1 < width && py + size - 1 < height) { int sourceX = Math.max(0,
	 * px + offsetX); int sourceY = Math.max(0, py + offsetY);
	 * 
	 * int actualWidth = size; int actualHeight = size;
	 * 
	 * if (sourceX + size > width) { actualWidth = width - sourceX; } if (sourceY +
	 * size > height) { actualHeight = height - sourceY; }
	 * 
	 * if (actualWidth > 0 && actualHeight > 0) { byte[] buffer = new
	 * byte[actualWidth * actualHeight * 4]; WritablePixelFormat<ByteBuffer>
	 * pixelFormat = PixelFormat.getByteBgraInstance();
	 * 
	 * pixelReader.getPixels(sourceX, sourceY, actualWidth, actualHeight,
	 * pixelFormat, buffer, 0, actualWidth * 4); pixelWriter.setPixels(px, py,
	 * actualWidth, actualHeight, pixelFormat, buffer, 0, actualWidth * 4); } } }
	 */
}