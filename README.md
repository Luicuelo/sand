# SandFX - Falling Sand Simulation

SandFX is a JavaFX-based particle physics simulation that implements a falling sand effect with interactive features. The simulation creates a granular physics environment where particles behave like sand, falling due to gravity and interacting with each other based on simple rules.

## Features

- Real-time falling sand particle simulation
- Interactive mouse controls to add particles
- Physics-based particle movement with gravity and collision detection
- Ability to apply forces to particles with mouse clicks
- Optimized rendering using JavaFX WritableImage

## Prerequisites

- Java 8 or higher
- JavaFX SDK (if not included in your Java distribution)

## Building and Running

### Using Command Line

1. Clone or download the repository
2. Navigate to the project directory
3. Compile the project:
   ```bash
   javac --module-path $PATH_TO_FX --add-modules javafx.controls principal/Sand.java
   ```
   
   Note: Replace `$PATH_TO_FX` with the path to your JavaFX SDK lib folder.
   
4. Run the application:
   ```bash
   java --module-path $PATH_TO_FX --add-modules javafx.controls principal.Sand
   ```

### Command Line Arguments

- `--width=value` - Set window width (default: 800)
- `--height=value` - Set window height (default: 600)

Example with custom dimensions:
```bash
java --module-path $PATH_TO_FX --add-modules javafx.controls principal.Sand --width=1024 --height=768
```

### Using an IDE

1. Import the project into your preferred IDE (IntelliJ IDEA, Eclipse, etc.)
2. Add JavaFX libraries to your project dependencies
3. Set the following VM options for running the project:
   ```
   --module-path $PATH_TO_FX --add-modules javafx.controls
   ```
4. Run the [Sand.java](file:///D:/Luis/Java/estados2/src/principal/Sand.java) main class

## Project Structure

### Main Components

1. **[Sand.java](file:///D:/Luis/Java/estados2/src/principal/Sand.java)** - Main JavaFX application class
   - Handles window creation and user interface
   - Manages the animation loop (60 FPS)
   - Processes mouse events for interaction
   - Controls application lifecycle

2. **[Renderer.java](file:///D:/Luis/Java/estados2/src/principal/Renderer.java)** - Rendering and simulation engine
   - Manages pixel data and palette
   - Handles particle generation at mouse position
   - Updates particle states using Transition rules
   - Renders the simulation to screen using block-based rendering
   - Processes hit forces applied to particles

3. **[Transition.java](file:///D:/Luis/Java/estados2/src/principal/Transition.java)** - Physics and transition rules
   - Defines patterns for particle behavior detection
   - Implements falling sand physics with gravity
   - Handles particle collisions and stacking
   - Manages lateral sliding when direct fall is blocked
   - Processes hit forces applied to particles

## Physics Simulation

### Particle Movement

The simulation implements a cellular automaton approach to particle physics:

1. **Gravity**: Particles fall downward when there's empty space below
2. **Stacking**: Particles stack on top of each other or at the bottom boundary
3. **Sliding**: When direct downward movement is blocked, particles slide diagonally
4. **Interaction**: Particles interact with boundaries (considered solid)

### Patterns

The system uses 7 predefined patterns to determine particle behavior:

- Pattern 0: Particle with empty space directly below (falls straight down)
- Pattern 1: Particle with empty space diagonally left-below
- Pattern 2: Particle with empty space diagonally right-below
- Pattern 3: Particle with no empty space around (stays in place)
- Pattern 4: Particle with empty space below (general case)
- Pattern 5: Particle with empty space to the right
- Pattern 6: Particle with empty space to the left

### Hit Forces

The simulation supports applying forces to particles with mouse clicks:
- Left-click applies a force from left to right
- Right-click applies a force from right to left
- Force strength is randomized between 1-10
- Probability of force application is proportional to hitForce value (10% to 100%)

## Technical Implementation

### Rendering Optimization

- Uses block-based rendering (4x4 pixel blocks per cell) for performance
- Implements double buffering with int arrays for efficient state updates
- Uses WritableImage and PixelWriter for direct pixel manipulation
- Employs BGRA byte array for fast block filling

### Mouse Interaction

- Mouse movement creates new particles at cursor position
- Clicking applies forces to existing particles
- Primary button (left-click) applies rightward force
- Secondary button (right-click) applies leftward force

### Color System

- Uses a 256-color palette system
- Color 0 is white (background)
- Colors 1-255 are randomly generated grayscale values
- Particle colors change over time based on frame counter

## Controls

- **Mouse Movement**: Create new particles at cursor position
- **Left Click**: Apply rightward force to particles
- **Right Click**: Apply leftward force to particles
- **Restart Button**: Reset the simulation

## Performance Considerations

The simulation is optimized for performance:
- Block-based rendering reduces draw calls
- Double buffering minimizes visual artifacts
- Pattern matching is optimized with early termination
- Memory is reused where possible to reduce garbage collection