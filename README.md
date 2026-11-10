# SandFX - Falling Sand Simulation

SandFX is a JavaFX-based particle physics simulation that implements a falling sand effect with interactive features. The simulation creates a granular physics environment where particles behave like sand, falling due to gravity and interacting with each other based on simple rules.

The design of the engine (materials, glyphs, rules and the reason behind each one)
has its own document: **[ENGINE.md](ENGINE.md)**, written in Spanish.

## Features

- Real-time falling sand particle simulation
- Five materials (sand, water, wood, fire, smoke), each with its own band of
  palette values: the colour of a particle is its age
- Interactive mouse controls to add particles
- A material selector, by button or by key, to paint with any of them
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
   - Ages the materials with life and applies the rules that transform them

4. **[Material.java](src/principal/Material.java)** - Materials and palette
   - One entry per material: glyph, size of its band of values and colours
   - Lays out the bands from value 1 in the order of the list
   - Holds the value to glyph table the rules ask for
   - Walks the band for the life glyphs (`V`, `W`, `r`, `l`)

## Physics Simulation

### Particle Movement

The simulation implements a cellular automaton approach to particle physics:

1. **Gravity**: Particles fall downward when there's empty space below
2. **Stacking**: Particles stack on top of each other or at the bottom boundary
3. **Sliding**: When direct downward movement is blocked, particles slide diagonally
4. **Interaction**: Particles interact with boundaries (considered solid)

### Rules

Every movement is a rule: a drawing that has to match the current generation
and a drawing that says what that neighbourhood becomes in the next one. Both
are written with the same glyphs:

| Glyph | In a match | In a result |
|-------|------------|-------------|
| `?` | any neighbour | nothing is written |
| `X` | a particle (the borders count as particles) | the particle is written there, with its value |
| `_` | free space | the cell is emptied |
| `S` `A` `M` `F` `H` | a value of that material | a new particle of that material |
| `s` `a` `m` `f` `h` | the last value of that material: it has spent its life | not used |
| `x` | same as `X` | one of these cells keeps the particle, drawn by chance |
| `o` | same as `X` | each of these cells rolls its own dice, so several can win: the winners are new particles that share the life the original has left, and nothing is born when there is nothing left to share |
| `V` | same as `X` | the particle one value older, wrapping back at the end |
| `W` | same as `X` | the particle one value older, staying at the last one |
| `r` | same as `X` | the particle with half of the life it has left |
| `l` | same as `X` | the particle with half of the life it has spent |

A rule reads as two stacked drawings, *now* and *after*: the rows are the rows
of the grid and the middle character is the particle being decided, so a rule
can be as small as one column (`M` → `X`, "wood stays where it is put") or as
big as the neighbourhood it needs, always with an odd size. The result has to
have the same shape as the match.

The `?` cells are left untouched on purpose: every particle is moved once, by
the rule of its own cell, and copying the neighbours would duplicate the ones
that already moved in the same step.

### Materials

A cell holds a palette value, so a material and its age travel together: the
position inside the band is the life of the particle. The bands are laid out
from value 1 in the order of `Material.LIST`, and value 0 is empty space.

| Glyph | Material | Values | Life | Born in | Colours |
|-------|----------|--------|------|---------|---------|
| `S` | sand | 1-25 | no | 1-25 | pale yellow to golden ochre |
| `A` | water | 26-45 | no | 26-45 | light blue to deep blue |
| `M` | wood | 46-75 | yes | 46-54 | light wood to dark wood |
| `F` | fire | 76-155 | yes | 76-81 | pale yellow to orange to red |
| `H` | smoke | 156-167 | yes | 156-159 | dark grey to light grey |

Every material is created at a random value among the first ones of its band
(`spread` in `Material.LIST`), so a pile is not a flat sheet of a single colour.
Sand and water spread over the whole band; the materials with life only over the
first few values, because there the value is the age and the rest is life spent
at birth. A material with life then walks towards the last value: that is what
`V` and `W` do, and the size of the band is how long it lasts. The band of the
fire is wide on purpose: climbing costs one value per row and the canvas is 75
rows tall, so a flame painted on the floor needs 80 steps of life to reach the
ceiling, and its spread is kept at 6 so that even the unluckiest flame gets
there.
The last value of a material with life is painted almost white, so a particle
that has spent its life is practically invisible while it waits for the rule
that transforms it.

### How the table is read

The rules are tried in order and the first match wins, so the table is a
priority list: the transitions of life first, then what every material does
that is not falling, and at the end the generic gravity that any particle
obeys.

| Rule | Match | Result | When |
|------|-------|--------|------|
| Fire ends | `f` | `H` | the flame has spent its life and becomes smoke |
| Smoke ends | `h` | `_` | the smoke has spent its life and disappears |
| Wood ends | `m` | `F` | the wood has spent its life and catches fire |
| Wood burns | fire on any of its four sides | `?W?` | the wood walks towards its end, one step per generation |
| Fire feeds | `???`<br>`?F?`<br>`?M?` | `???`<br>`?l?`<br>`???` | there is wood below: the flame recovers half of the life it has spent, which is what keeps it alive while there is fuel |
| Wood stays | `M` | `X` | wood never falls, and it keeps its value instead of being born again |
| Fire rises | `?_?`<br>`?F?`<br>`???` | `?W?`<br>`?_?`<br>`???` | free space above: the flame rises one row |
| Fire spreads | `???`<br>`?F?`<br>`???` | `?o?`<br>`oWo`<br>`?o?` | the flame cannot rise: it ages in place and copies itself to the four cells around it, one in four each, giving every copy a quarter of the life it has left |
| Smoke rises | `?_?`<br>`?H?`<br>`???` | `?W?`<br>`?_?`<br>`???` | smoke rises like fire, but it neither spreads nor creates anything |
| Smoke ages | `?H?` | `?W?` | the smoke cannot rise: it ages where it is, so smoke trapped under the sand still fades out and disappears |
| Water flows | `_A_`<br>`XXX` | `x_x` | both sides free over a solid row: the water slides to one of them and levels its surface |
| Free fall | `???`<br>`?X?`<br>`?_?` | `???`<br>`?_?`<br>`?X?` | free space below, the particle drops one row |
| Slide both | `???`<br>`?X?`<br>`_X_` | `???`<br>`?_?`<br>`x?x` | something below, both diagonals free: one of them, half of the time each |
| Slide left | `???`<br>`?X?`<br>`_XX` | `???`<br>`?_?`<br>`X??` | only the left diagonal is free |
| Slide right | `???`<br>`?X?`<br>`XX_` | `???`<br>`?_?`<br>`??X` | only the right diagonal is free |
| Stay | `???`<br>`?X?`<br>`XXX` | `???`<br>`?X?`<br>`???` | no free cell around (the border counts as solid), the particle stacks |

The bottom row of a result is the row below the particle, so `X??` means "moved
to the cell down-left". The rules of the water are the same drawing with only
one free side (`_AX`, `XA_`), so a grain of water can also flow alone.

Hit forces use the same language with two more rules: `?X_` → `?_X` when the
particle is pushed right and `_X?` → `X_?` when it is pushed left.

### Hit Forces

The simulation supports applying forces to particles with mouse clicks:
- Left-click applies a force towards the left
- Right-click applies a force towards the right
- Force strength is randomized between 1 and 10 (`Transition.MAX_HIT_FORCE`)
- Probability of force application is proportional to hitForce value (10% to 100%)
- A particle is only pushed when the cell on that side is free, so a hit never
  squeezes particles into each other

## Technical Implementation

### Rendering Optimization

- Uses block-based rendering (8x8 pixel blocks per cell) for performance
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
- Value 0 is white (background)
- The rest of the palette is one band per material, so the colour of a cell is
  the age of the particle and not a decoration
- A band is interpolated between a few stops, which is where the ramp of each
  material is written
- The last value of a material with life is tinted almost white, so a spent
  particle is a ghost that waits for the rule that transforms it

## Controls

- **Mouse Movement**: Create new particles at cursor position
- **Material Buttons**: Pick the material the mouse paints (the title shows the
  glyph of the chosen one)
- **Material Keys**: `S` sand, `A` water, `M` wood, `F` fire, `H` smoke: the same
  as pressing the button
- **Hold Space**: Paint a wider brush around the mouse
- **Left Click**: Apply a force to the left
- **Right Click**: Apply a force to the right
- **Restart Button**: Reset the simulation

## Performance Considerations

The simulation is optimized for performance:
- Block-based rendering reduces draw calls
- Double buffering minimizes visual artifacts
- Pattern matching is optimized with early termination
- Memory is reused where possible to reduce garbage collection