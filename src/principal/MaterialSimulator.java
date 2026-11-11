package principal;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

import java.util.HashMap;
import java.util.Map;

/**
 * Main JavaFX application of the materials simulator.
 * <p>
 * This application creates a window with a canvas where users can interact with
 * a falling materials physics simulation. Users can add particles by moving the
 * mouse over the canvas and apply forces by clicking. The simulation runs at
 * 60 FPS.
 * </p>
 * <p>
 * Features:
 * <ul>
 *   <li>Real-time particle physics simulation</li>
 *   <li>Interactive mouse controls for adding particles and applying forces</li>
 *   <li>A material selector, with one button and one key per material</li>
 *   <li>60 FPS animation loop</li>
 *   <li>Restart functionality</li>
 *   <li>Configurable window size via command-line parameters</li>
 * </ul>
 * 
 * @see WorldRenderer
 * @see MaterialRules
 */
public class MaterialSimulator extends Application {
	private static final String TITLE = "MaterialSimulator";
	private static final int DEFAULT_WIDTH = 800;
	private static final int DEFAULT_HEIGHT = 600;
	private static final int BUTTON_HEIGHT = 25;
	private static final int BUTTON_SPACING = 6;

	private int canvasWidth;
	private int canvasHeight;
	private Canvas canvas;
	private WorldRenderer renderer;
	private AnimationTimer animationTimer;
	private boolean finished = false;

	/**
	 * Main entry point for the application.
	 * 
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		launch(args);
	}

	/**
	 * Starts the JavaFX application and initializes the user interface.
	 * 
	 * @param primaryStage the primary stage for this application
	 */
	@Override
	public void start(Stage primaryStage) {
		// Parse command line arguments for size
		Parameters params = getParameters();
		parseSize(params);

		primaryStage.setTitle(TITLE);

		// Create and initialize
		initialize();

		// Setup mouse listeners
		setupMouseListeners();

		// Create the restart button
		Button restartButton = new Button("Restart");
		restartButton.setOnAction(e -> restart());
		restartButton.setPrefHeight(BUTTON_HEIGHT);
		restartButton.setFocusTraversable(false);

		// One button per material, tinted with its own colour. The keys and the
		// buttons share the map, so pressing "F" is the same as pressing the
		// button of the fire, and both report the change to the renderer. A
		// material the brush cannot paint, such as the water that is holding a
		// grain, is left out of both
		ToggleGroup materialButtons = new ToggleGroup();
		Map<Character, ToggleButton> buttonsByGlyph = new HashMap<>();
		HBox toolbar = new HBox(BUTTON_SPACING, restartButton);
		toolbar.setAlignment(Pos.CENTER_LEFT);
		toolbar.setPadding(new Insets(4, 8, 4, 8));
		for (Material material : Material.LIST) {
			if (!material.paintable)
				continue;
			ToggleButton button = new ToggleButton(String.valueOf(material.glyph));
			button.setUserData(material);
			button.setToggleGroup(materialButtons);
			button.setPrefHeight(BUTTON_HEIGHT);
			button.setFocusTraversable(false);
			button.setStyle(css(material.tint()));
			buttonsByGlyph.put(material.glyph, button);
			toolbar.getChildren().add(button);
		}
		materialButtons.selectedToggleProperty().addListener((observable, previous, current) -> {
			if (current == null) {
				// Clicking the selected button would leave the brush without a
				// material: the current one is put back
				materialButtons.selectToggle(previous);
				return;
			}
			Material material = (Material) current.getUserData();
			renderer.selectMaterial(material.glyph);
			primaryStage.setTitle(TITLE + " - " + material.glyph);
		});
		materialButtons.selectToggle(buttonsByGlyph.get(Material.LIST[0].glyph));

		// Layout
		BorderPane root = new BorderPane();
		root.setCenter(canvas);
		root.setBottom(toolbar);

		// Create scene and show. The size is not given here: the canvas knows its
		// own, the toolbar takes what its buttons need and the scene asks the root
		// for the total. Giving it by hand would clip the toolbar as soon as the
		// buttons needed one pixel more than expected
		Scene scene = new Scene(root);
		scene.setOnKeyPressed(e -> {
			if (renderer == null)
				return;
			if (e.getCode() == KeyCode.SPACE) {
				renderer.setSpacePressed(true);
				return;
			}
			if (e.getCode().isLetterKey()) {
				ToggleButton button = buttonsByGlyph.get(Character.toUpperCase(e.getCode().getChar().charAt(0)));
				if (button != null)
					button.setSelected(true);
			}
		});
		scene.setOnKeyReleased(e -> {
			if (e.getCode() == KeyCode.SPACE && renderer != null) {
				renderer.setSpacePressed(false);
			}
		});
		primaryStage.setScene(scene);
		primaryStage.setResizable(false);
		primaryStage.show();
	}

	/**
	 * Parses command line arguments for canvas size.
	 * 
	 * @param params the application parameters containing named arguments
	 */
	private void parseSize(Parameters params) {
		try {
			if (params.getNamed().containsKey("width")) {
				canvasWidth = Integer.parseInt(params.getNamed().get("width"));
			} else {
				canvasWidth = DEFAULT_WIDTH;
			}

			if (params.getNamed().containsKey("height")) {
				canvasHeight = Integer.parseInt(params.getNamed().get("height"));
			} else {
				canvasHeight = DEFAULT_HEIGHT;
			}
		} catch (NumberFormatException e) {
			System.err.println("Invalid size parameters, using defaults");
			canvasWidth = DEFAULT_WIDTH;
			canvasHeight = DEFAULT_HEIGHT;
		}
	}

	/**
	 * Initializes the canvas and starts the animation loop. Sets up the graphics
	 * context and begins 60 FPS rendering.
	 */
	private void initialize() {
		canvas = new Canvas(canvasWidth, canvasHeight);
		GraphicsContext gc = canvas.getGraphicsContext2D();
		finished = false;

		renderer = new WorldRenderer(gc, canvasWidth, canvasHeight);

		animationTimer = new AnimationTimer() {
			@Override
			public void handle(long now) {
				if (!finished && renderer != null) {
					try {
						renderer.tick();
					} catch (Exception e) {
						System.err.println("Error updating : " + e.getMessage());
						stopAnimation();
					}
				}
			}
		};
		animationTimer.start();
	}

	/**
	 * Configures mouse event handlers for interactive effects. Tracks mouse
	 * movement, entry, and exit events on the canvas. Also handles mouse clicks
	 * for applying forces to particles in the simulation.
	 */
	private void setupMouseListeners() {
		canvas.setOnMouseMoved(e -> {
			if (renderer != null) {
				renderer.setMousePosition((int) e.getX(), (int) e.getY(), true);
			}
		});

		canvas.setOnMouseExited(e -> {
			if (renderer != null) {
				renderer.setMousePosition(0, 0, false);
			}
		});

		canvas.setOnMouseEntered(e -> {
			if (renderer != null) {
				renderer.setMousePosition((int) e.getX(), (int) e.getY(), true);
			}
		});
		
		canvas.setOnMouseClicked(e -> {
			if (renderer != null) {
				renderer.applyHit(e.getButton() == MouseButton.PRIMARY);
			}
		});
	}

	/**
	 * Restarts the simulation by reinitializing the renderer.
	 */
	private void restart() {
		if (renderer != null) {
			renderer.restart();
		}
	}

	/**
	 * Inline style of a button tinted with a colour.
	 * <p>
	 * The default stylesheet of JavaFX derives the shades of a control from
	 * {@code -fx-base}, so a single property tints the whole button, both pressed
	 * and released, without setting a background of its own.
	 * </p>
	 *
	 * @param color the colour of the material the button selects
	 * @return the style to set on the button
	 */
	private static String css(Color color) {
		return String.format("-fx-base: #%02x%02x%02x;", (int) (color.getRed() * 255), (int) (color.getGreen() * 255),
				(int) (color.getBlue() * 255));
	}

	/**
	 * Stops the animation timer and cleans up resources.
	 */
	private void stopAnimation() {
		finished = true;
		if (animationTimer != null) {
			animationTimer.stop();
		}
	}

	/**
	 * Called when the application is about to stop. Cleans up resources and exits.
	 */
	@Override
	public void stop() {
		stopAnimation();
		renderer = null;
		Platform.exit();
	}
}