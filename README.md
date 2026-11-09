# SandFX - Falling Sand Simulation

SandFX is a JavaFX-based particle physics simulation that implements a falling sand effect with interactive features. The simulation creates a granular physics environment where particles behave like sand, falling due to gravity and interacting with each other based on simple rules.

## Features

- Real-time falling sand particle simulation
- Interactive mouse controls to add particles
- Physics-based particle movement with gravity and collision detection
- Ability to apply forces to particles with mouse clicks
- Optimized rendering using JavaFX WritableImage

## Prerequisites

- Java 17 or higher
- JavaFX SDK 21 (if not included in your Java distribution)

## Building and Running

### Using Command Line

1. Clone or download the repository
2. Navigate to the project directory
3. Compile the project:
   ```bash
   javac -Xlint:all --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.graphics -d bin src/principal/*.java
   ```
   
   Note: Replace `$PATH_TO_FX` with the path to your JavaFX SDK lib folder.
   
4. Run the application:
   ```bash
   java --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.graphics -cp bin principal.Sand
   ```

### Command Line Arguments

- `--width=value` - Set window width (default: 800)
- `--height=value` - Set window height (default: 600)

Example with custom dimensions:
```bash
java --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.graphics -cp bin principal.Sand --width=1024 --height=768
```

### Using an IDE

1. Import the project into your preferred IDE (IntelliJ IDEA, Eclipse, etc.)
2. Add JavaFX libraries to your project dependencies
3. Set the following VM options for running the project:
   ```
   --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.graphics
   ```
4. Run the [Sand.java](src/principal/Sand.java) main class

## Project Structure

### Main Components

1. **[Sand.java](src/principal/Sand.java)** - Main JavaFX application class
   - Handles window creation and user interface
   - Manages the animation loop (60 FPS)
   - Processes mouse events for interaction
   - Controls application lifecycle

2. **[Renderer.java](src/principal/Renderer.java)** - Rendering and simulation engine
   - Manages pixel data and palette
   - Handles particle generation at mouse position
   - Updates particle states using Transition rules
   - Renders the simulation to screen using block-based rendering
   - Processes hit forces applied to particles

3. **[Transition.java](src/principal/Transition.java)** - Physics and transition rules
   - Defines the rule tables (match and result patterns) for particle behaviour
   - Implements falling sand physics with gravity
   - Handles particle collisions and stacking
   - Manages lateral sliding when direct fall is blocked
   - Pushes particles sideways for hit forces

## Physics Simulation

### Particle Movement

The simulation implements a cellular automaton approach to particle physics:

1. **Gravity**: Particles fall downward when there's empty space below
2. **Stacking**: Particles stack on top of each other or at the bottom boundary
3. **Sliding**: When direct downward movement is blocked, particles slide diagonally
4. **Interaction**: Particles interact with boundaries (considered solid)

### Rules

Every movement is a rule: a 3x3 neighbourhood that has to match the current
generation and a 3x3 result that says what that neighbourhood becomes in the
next one. Both are written with the same four glyphs:

| Glyph | In a match | In a result |
|-------|------------|-------------|
| `?` | any neighbour | nothing is written |
| `X` | a particle (the borders count as particles) | the particle is written there |
| `_` | free space | the cell is emptied |
| `x` | same as `X` | one of these cells keeps the particle, drawn by chance |

A rule therefore reads as two stacked drawings, *now* and *after*. Gravity
tries the rules below in order and the first match decides the move:

| Rule | Match | Result | When |
|------|-------|--------|------|
| Free fall | `???`<br>`?X?`<br>`?_?` | `???`<br>`?_?`<br>`?X?` | free space below, the particle drops one row |
| Slide both | `???`<br>`?X?`<br>`_X_` | `???`<br>`?_?`<br>`x?x` | something below, both diagonals free: one of them, half of the time each |
| Slide left | `???`<br>`?X?`<br>`_XX` | `???`<br>`?_?`<br>`X??` | only the left diagonal is free |
| Slide right | `???`<br>`?X?`<br>`XX_` | `???`<br>`?_?`<br>`??X` | only the right diagonal is free |
| Stay | `???`<br>`?X?`<br>`XXX` | `???`<br>`?X?`<br>`???` | no free cell around (the border counts as solid), the particle stacks |

The bottom row of a result is the row below the particle, so `X??` means "moved
to the cell down-left". The `?` cells are left untouched on purpose: every
particle is moved once by the rule of its own cell, and copying the neighbours
would duplicate the ones that already moved in the same step.

Hit forces use the same language with two more rules: `?X_` → `?_X` when the
particle is pushed right and `_X?` → `X_?` when it is pushed left.

### Hit Forces

The simulation supports applying forces to particles with mouse clicks:
- Left-click applies a force from left to right
- Right-click applies a force from right to left
- Force strength is randomized between 1 and 10 (`Transition.MAX_HIT_FORCE`)
- Probability of force application is proportional to hitForce value (10% to 100%)
- A particle is only pushed when the cell on that side is free, so a hit never
  squeezes particles into each other

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