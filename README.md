# MaterialSimulator - Falling Materials Simulation

MaterialSimulator is a JavaFX-based particle physics simulation that implements a
falling materials effect with interactive features. The simulation creates a
granular physics environment where particles behave like sand, water, wood, fire
or smoke, falling due to gravity and interacting with each other based on simple
rules.

The repository holds two engines side by side, each one with its own package and
its own main class:

| Package | Main class | What it is |
|---|---|---|
| `src/principal` | `principal.MaterialSimulator` | The materials simulator: five paint-able materials plus the mark of the water that holds a grain, a clock per material, hit forces |
| `src/sand` | `sand.Sand` | The original falling sand, recovered from commit `a2679ca` and left as it was |

The design of the engine (materials, glyphs, rules and the reason behind each one)
has its own document: **[ENGINE.md](ENGINE.md)**, written in Spanish.

## Features

- Real-time falling sand particle simulation
- Five materials (sand, water, wood, fire, smoke), each with its own band of
  palette values: the colour of a particle is its age
- Sand that sinks in water, slowly: the drop that holds a grain waits and then
  the two swap places
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
   javac -Xlint:all --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.graphics -d bin src/principal/*.java src/sand/*.java
   ```
   
   Note: Replace `$PATH_TO_FX` with the path to your JavaFX SDK lib folder.
   
4. Run the application:
   ```bash
   java --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.graphics -cp bin principal.MaterialSimulator
   ```
   
   The original version runs with `-cp bin sand.Sand`.

### Command Line Arguments

- `--width=value` - Set window width (default: 800)
- `--height=value` - Set window height (default: 600)

Example with custom dimensions:
```bash
java --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.graphics -cp bin principal.MaterialSimulator --width=1024 --height=768
```

### Using an IDE

1. Import the project into your preferred IDE (IntelliJ IDEA, Eclipse, etc.)
2. Add JavaFX libraries to your project dependencies
3. Set the following VM options for running the project:
   ```
   --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.graphics
   ```
4. Run the [MaterialSimulator.java](src/principal/MaterialSimulator.java) main
   class, or [Sand.java](src/sand/Sand.java) for the original version

## Project Structure

### Main Components

1. **[MaterialSimulator.java](src/principal/MaterialSimulator.java)** - Main JavaFX application class
   - Handles window creation and user interface
   - Manages the animation loop (60 FPS)
   - Processes mouse events for interaction
   - Controls application lifecycle

2. **[WorldRenderer.java](src/principal/WorldRenderer.java)** - Rendering and simulation engine
   - Manages pixel data and palette
   - Handles particle generation at mouse position, at half the clock of the selected material
   - Steps the world `ENGINE_MULTIPLIER` times per frame (four times today) and draws the picture on every frame
   - Updates particle states using the rules table, only for the particles whose clock marks the step
   - Renders the simulation to screen using block-based rendering
   - Processes hit forces applied to particles

3. **[MaterialRules.java](src/principal/MaterialRules.java)** - Physics and transition rules
   - Defines the rule tables (match and result patterns) for particle behaviour
   - Implements the gravity, the diagonal slides and the stacking of the particles
   - Handles particle collisions and stacking
   - Manages lateral sliding when direct fall is blocked
   - Pushes particles sideways for hit forces
   - Ages the materials with life and applies the rules that transform them

4. **[Material.java](src/principal/Material.java)** - Materials and palette
   - One entry per material: glyph, size of its band of values, clock and colours
   - Lays out the bands from value 1 in the order of the list
   - Holds the value to glyph table the rules ask for
   - Holds the clock of each material: how many steps of the world pass between two steps
   - Walks the band for the life glyphs (`V`, `W`, `r`, `l`)

5. **[Sand.java](src/sand/Sand.java)** - The original engine, in its own package
   - The falling sand simulator as it was in commit `a2679ca`: one class for the
     window, one for the rules and one for the picture, with no materials table
   - Kept apart in `src/sand` so both versions can be compiled and run side by side

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
| `S` `A` `M` `F` `H` `N` | a value of that material | a new particle of that material |
| `s` `a` `m` `f` `h` `n` | the last value of that material: it has spent its life (for the mark `n`, it has waited long enough) | not used |
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

| Glyph | Material | Values | Life | Born in | Clock | Colours |
|-------|----------|--------|------|---------|-------|---------|
| `S` | sand | 1-25 | no | 1-25 | 6 | pale yellow to golden ochre |
| `A` | water | 26-45 | no | 26-45 | 1 | light blue to deep blue |
| `M` | wood | 46-75 | yes | 46-54 | 3 | light wood to dark wood |
| `F` | fire | 76-105 | yes | 76-81 | 3 | pale yellow to orange to red |
| `H` | smoke | 106-185 | yes | 106-111 | 3 | dark grey to light grey |
| `N` | water holding a grain | 186-191 | no, the band is the wait | 186 | 6 | water blue, a shade darker |

Every material is created at a random value among the first ones of its band
(`spread` in `Material.LIST`), so a pile is not a flat sheet of a single colour.
Sand and water spread over the whole band; the materials with life only over the
first few values, because there the value is the age and the rest is life spent
at birth. A material with life then walks towards the last value: that is what
`V` and `W` do, and the size of the band is how long it lasts. Since climbing
costs one value per row and the canvas is 75 rows tall, the width of each band is
a distance on the screen: the fire gets 30 values, a third of the height, and the
smoke gets 80 so that it really does reach the ceiling.
The last value of a material with life is painted almost white, so a particle
that has spent its life is practically invisible while it waits for the rule
that transforms it.

`N` is the exception: it is not a material of the catalogue but a **state** of a
drop, the one that is holding a grain of sand and waiting to swap places with it.
Its band counts turns and not a life, it is not painted by the brush (so it has
no button and no key) and, having no life, it is never tinted white: the wait
would blink once per grain.

### Clocks

There are two counters and each one has a job. The **frame counter** counts
pictures: the hit and the drawing divide it. The **step counter** counts turns of
the world: the materials and the brush divide it. `ENGINE_MULTIPLIER` is how many
steps the world takes per frame, and it is the knob that makes everything faster
or slower at once, without touching a single rule:

- the **water** has a clock of 1, so it steps on every step (240 a second);
- the **sand** has a clock of 6, so it steps one step in six (40 a second): it
  falls and spreads at a sixth of the speed of the liquid;
- the **wood**, the **fire**, the **smoke** and the **mark** that holds a grain
  have a clock of 3 or 6, and the ones with life keep the pace of their lives,
  which are counted in steps: they are stepped 80 times a second, and each turn
  is one row they climb or one step older.

With the multiplier at 4 the world takes four steps per frame: the water falls
four cells per frame and the sand one cell every frame and a half. The mark of a
drop that holds a grain is stepped one step in six, so the grain waits nine
frames for every row it sinks, which is the slow fall this whole group of rules
is about.

The step a particle does not get is not a pause: it is copied to the next
generation untouched, so it keeps its place, its colour and its life. That is
what makes a liquid look like a liquid next to a grain without changing the
grain at all.

The clock and the painting are separate, and both fit easily. `WorldRenderer.tick()`
runs sixty times a second and paints on every frame (`FRAMES_PER_PAINT = 1`);
measured on the default canvas, filling the whole buffer costs 0.95 ms and the
four steps of the world 0.45 ms, out of the 16.6 ms a frame has at 60 FPS.

The brush follows the same clock, at half speed. It runs inside the step and
drips one particle every two turns of the selected material, so the sand drips
one grain every twenty-four steps and the water one every two. The turn in
between is what leaves the grain room to fall: painted on every turn, a grain is
born on the cell the one before has just left, the previous generation still
shows it there, so it slides sideways in the air and the jet opens into a cone.
With the gap the jet is one cell wide instead of three.

The sweep swaps sides **on every frame, not on every step**, so what matters is in
which frames the turns of a material land: a grain is due one step in six and a
frame has four, so its turns fall in frames 0, 1, 3, 4, 6 and so on, and they see
both directions. Were the turns always on the same side, every one of them would
read its rows in the same direction and push its pile sideways: alternating per
step, or with an even quotient between clock and multiplier, a block of sand ends
up 5.4 cells to one side and visibly flatter (13 rows instead of 17 and 42 columns
instead of 37). With the clocks of today a pile of 200 grains dropped on a pond
stays 0.06 cells from the centre. Materials faster than the frame (water with 1,
wood, fire and smoke with 3) are stepped several times per frame and see both
sides anyway.

### How the table is read

The rules are tried in order and the first match wins, so the table is a
priority list: the flames that die in water first, then the transitions of life,
then what every material does that is not falling (the drop that jumps into the
cell of the flame it has just put out, among them), and at the end the generic
gravity that any particle obeys.

| Rule | Match | Result | When |
|------|-------|--------|------|
| Fire dies under water | water on any of its eight sides | `?_?` | the flame touches water: it is consumed where it is instead of rising or spreading, which is what stops a fire from jumping over a drop |
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
| Water puts out | `?F?`<br>`?A?` | `?X?`<br>`?_?` | the drop jumps into the cell the flame has just left, so the water follows the fire it has put out. Only upwards: the flame above is stepped first, so its cell is already free, while below or at the side the drop would move first and the flame would write over it |
| Water is held | `?S?`<br>`?A?`<br>`???` | `???`<br>`?N?`<br>`???` | a grain rests on the drop: it becomes the mark that counts the wait, so the grain neither sinks nor slides while the drop holds it |
| Held rises | `?S?`<br>`?n?`<br>`???` | `?V?`<br>`???`<br>`???` | the wait is over: the mark climbs into the cell the grain has just left, and `V` sends it back to the first value, so the next grain waits exactly as long |
| Held ages | `?S?`<br>`?N?`<br>`???` | `???`<br>`?W?`<br>`???` | the grain is still there: the mark walks one value towards the end of its band, which is the wait |
| Sand sinks | `???`<br>`?S?`<br>`?n?` | `???`<br>`?_?`<br>`?X?` | the wait is over: the grain empties its cell and drops into the one of the mark |
| Held releases | `?N?` | `?A?` | no grain on top: the sand slid away and the mark is a drop again |
| Water flows | `_A_`<br>`?X?` | `x_x` | both sides free and something below: the water slides to one of them and levels its surface |
| Free fall | `???`<br>`?X?`<br>`?_?` | `???`<br>`?_?`<br>`?X?` | free space below, the particle drops one row |
| Slide both | `???`<br>`?X?`<br>`_X_` | `???`<br>`?_?`<br>`x?x` | something below, both diagonals free: one of them, half of the time each |
| Slide left | `???`<br>`?X?`<br>`_XX` | `???`<br>`?_?`<br>`X??` | only the left diagonal is free |
| Slide right | `???`<br>`?X?`<br>`XX_` | `???`<br>`?_?`<br>`??X` | only the right diagonal is free |
| Stay | `???`<br>`?X?`<br>`XXX` | `???`<br>`?X?`<br>`???` | no free cell around (the border counts as solid), the particle stacks |

The bottom row of a result is the row below the particle, so `X??` means "moved
to the cell down-left". The rules of the water are the same drawing with only
one free side (`_AX`, `XA_`), so a grain of water can also flow alone.

The five rules of the held water are one mechanism seen from several sides, and they
work in pairs. The swap needs two rules, one per particle, because a rule only ever
writes its own particle: the grain cannot move the drop and the drop cannot move the
grain. The two drawings that meet are made so that they do not erase each other:
`Sand sinks` empties the cell of the grain, so the drop has somewhere to climb into,
and `Held rises` writes nothing in its own cell, so the grain that has just landed
there survives. That works because the rows are swept from the top: the grain is
always stepped first, and the cell it leaves is empty when the drop asks for it.
With a `_` in the middle of `Held rises`, or with the rows swept the other way, one
of the two disappeared in every swap.

`Held rises` goes before `Held ages` even though it looks backwards: `?N?` matches
the whole band, the last value included, so an ageing rule first would leave the
mark of a drop that has waited stuck at the end of its band, and the grain that
should sink on top of it would stay there for ever. The whole group goes before
`Free fall`, next to the water, because the mark is a state of a drop: without a
grain on top it becomes water again before it can move.

Measured on a column of 200 grains over a pond of 800 drops, 900 frames: 200 grains
and 800 drops before and after, not one mark left, no drop trapped under the pile,
217 marks at the same time in the worst moment (a third of the pond frozen while the
column crosses it), the level of the pond five rows higher, which is the volume of
the grains, a single grain sinking nine frames per row against one and a half in the
air, and the pile 0.06 cells from the centre where it was dropped. The fire does not
recognise `N` yet: the eight rules that put a flame out ask for `A`.

Inside the generic gravity the water levelling goes before the diagonal slides:
a liquid has no angle of repose, so a drop that has landed opens sideways and
only rolls down a slope when it has no free side left. With the diagonals first
the water piles up in the same cone as the sand. The row below of the levelling
is asked only for its own cell, because the free fall has already been tried and
the diagonals only block the levelling over an uneven bed.

Hit forces use the same language with two more rules: `?X_` → `?_X` when the
particle is pushed right and `_X?` → `X_?` when it is pushed left.

### Hit Forces

The simulation supports applying forces to particles with mouse clicks:
- Left-click applies a force towards the left
- Right-click applies a force towards the right
- Force strength is randomized between 1 and 10 (`MaterialRules.MAX_HIT_FORCE`)
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

- Mouse movement creates new particles at cursor position, at half the pace of
  the selected material, so each grain is born over free space and the jet falls
  as a column
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
- The speed of the world and the rate of the picture are separate knobs: the
  world takes `ENGINE_MULTIPLIER` steps per frame (`4` today) and the picture is
  rebuilt every frame (`FRAMES_PER_PAINT = 1`). Measured on the default canvas,
  the four steps cost 0.45 ms and the whole redraw 0.95 ms, out of the 16.6 ms a
  frame has at 60 FPS