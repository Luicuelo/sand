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
| `src/principal` | `principal.MaterialSimulator` | The materials simulator: five materials, a clock per material, hit forces |
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
   - Steps the world `ENGINE_MULTIPLIER` times per frame (nine times today) and draws the picture on every frame
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
   - Walks the band for the life glyphs (`V`, `W`, `r`, `l`) and draws a value again (`B`)

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
| `S` `A` `M` `F` `H` | a value of that material | a new particle of that material |
| `s` `a` `m` `f` `h` | the last value of that material: it has spent its life (for the water, it has waited long enough) | not used |
| `x` | same as `X` | one of these cells keeps the particle, drawn by chance |
| `o` | same as `X` | each of these cells rolls its own dice, so several can win: the winners are new particles that share the life the original has left, and nothing is born when there is nothing left to share |
| `V` | same as `X` | the particle one value older, wrapping back at the end |
| `W` | same as `X` | the particle one value older, staying at the last one |
| `B` | same as `X` | the particle with a value drawn again among the first ones of its band, like a new particle |
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
| `S` | sand | 1-25 | no | 1-25 | 12 | pale yellow to golden ochre |
| `A` | water | 26-82 | no (the tail of the band is the wait) | 26-33 | 1 | mid blue to light blue, and the wait flat |
| `M` | wood | 83-112 | yes | 83-91 | 9 | light wood to dark wood |
| `F` | fire | 113-142 | yes | 113-118 | 9 | pale yellow to orange to red |
| `H` | smoke | 143-222 | yes | 143-148 | 9 | dark grey to light grey |

Every material is created at a random value among the first ones of its band
(`spread` in `Material.LIST`), so a pile is not a flat sheet of a single colour.
The materials with life spread only over the first few values, because there the
value is the age and the rest is life spent at birth. A material with life then
walks towards the last value: that is what `V` and `W` do, and the size of the band
is how long it lasts. Since climbing costs one value per row and the canvas is 75
rows tall, the width of each band is a distance on the screen: the fire gets 30
values, a third of the height, and the smoke gets 80 so that it really does reach
the ceiling.
The last value of a material with life is painted almost white, so a particle
that has spent its life is practically invisible while it waits for the rule
that transforms it.

The water is the one band with two jobs. Its first eight values are the shades a
drop is born with; from there to the end the band is the **wait** of a grain of
sand resting on it, walked one value per turn by `W`, and the grain sinks when the
drop reaches the last value. The stops of that stretch are the same colour, so a
drop that is waiting does not change the colour of the pond, and as the band has no
life it is never tinted white: the wait would blink once per grain. The width is
measured as well: a drop born anywhere in the first eight values needs between 49
and 56 steps to reach the end, which is more than four turns of the grain (48) and
less than five (60), so every grain sinks on its fifth turn and a row always costs
60 steps of the world, 6.7 frames at the current multiplier.

### Clocks

There are two counters and each one has a job. The **frame counter** counts
pictures: the hit and the drawing divide it. The **step counter** counts turns of
the world: the materials and the brush divide it. `ENGINE_MULTIPLIER` is how many
steps the world takes per frame, and it is the knob that makes everything faster
or slower at once, without touching a single rule:

- the **water** has a clock of 1, so it steps on every step (540 a second);
- the **sand** has a clock of 12, so it steps one step in twelve (45 a second): it
  falls and spreads at a twelfth of the speed of the liquid;
- the **wood**, the **fire** and the **smoke** have a clock of 9, and the ones with
  life keep the pace of their lives, which are counted in steps: they are stepped
  60 times a second, once per frame, and each turn is one row they climb or one
  step older.

With the multiplier at 9 the world takes nine steps per frame: the water falls
nine cells per frame and the sand one cell every frame and a third. A grain waits
six frames and two thirds for every row it sinks, against a frame and a third in
the air, which is the slow fall this whole group of rules is about.

The step a particle does not get is not a pause: it is copied to the next
generation untouched, so it keeps its place, its colour and its life. That is
what makes a liquid look like a liquid next to a grain without changing the
grain at all.

The clock and the painting are separate, and both fit easily. `WorldRenderer.tick()`
runs sixty times a second and paints on every frame (`FRAMES_PER_PAINT = 1`);
measured on the default canvas, filling the whole buffer costs 0.95 ms and each
step of the world 0.11 ms (0.9 ms for the eight of a frame, measured with the
multiplier at 8), out of the 16.6 ms a frame has at 60 FPS.

The brush follows the same clock, at half speed. It runs inside the step and
drips one particle every two turns of the selected material, so the sand drips
one grain every twenty-four steps (every two frames and two thirds) and the water
one every two (four and a half per frame). The turn in
between is what leaves the grain room to fall: painted on every turn, a grain is
born on the cell the one before has just left, the previous generation still
shows it there, so it slides sideways in the air and the jet opens into a cone.
With the gap the jet is one cell wide instead of three.

The sweep swaps sides **on every frame, not on every step**, so what matters is in
which frames the turns of a material land: a grain is due one step in twelve and a
frame has nine, so its turns fall in frames 0, 1, 2, 4, 5, 6, 8, 9 and so on, and
they see both directions. Were the turns always on the same side, every one of them
would read its rows in the same direction and push its pile sideways: alternating
per step, or with an even quotient between clock and multiplier, a block of sand
ends up 5.4 cells to one side and visibly flatter (13 rows instead of 17 and 42
columns instead of 37). Materials faster than the frame (the water, with 1) are
stepped several times per frame and see both sides anyway; the ones with life,
with 9, land on every frame and see both too.

The multiplier is **odd** on purpose, and so must be the product with
`FRAMES_PER_PAINT`. The water falls one cell per step, nine per frame, and the
brush drops a particle every two steps, so a falling jet is a chain of drops two
cells apart. With an even stride the chain always lands on rows of the same parity
and every picture is congruent with the previous one: the jet looks like a dashed
line frozen in the air (the wagon-wheel effect), with the rows of the other parity
never occupied when the picture is taken. Odd, the chain moves one cell off its own
lattice on every frame and the fall is seen.

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
| Water waits | `?S?`<br>`?A?`<br>`???` | `???`<br>`?W?`<br>`???` | a grain rests on the drop: the drop walks one value towards the end of its band, and that walk is the wait. Nothing is moved and the stretch of the band is one flat colour, so the pond does not change while a grain sits on it |
| Water forgets | `?a?` | `?A?` | no grain on top: the sand slid away, so the drop is born again with one of the shades of a new drop instead of keeping the flat shade of the wait for ever |
| Sand sinks | `???`<br>`?S?`<br>`AaA` | `???`<br>`???`<br>`ppp` | the drop below has reached the end of its band: the grain changes places with one of the three drops under it, drawn at random, one in three each |
| Sand sinks straight | `???`<br>`?S?`<br>`?a?` | `???`<br>`???`<br>`?P?` | the same when the two diagonals are not water, at the wall of the pool or on a pile: the grain changes places with the drop below, without choosing |
| Water flows | `_A_`<br>`?X?` | `x_x` | both sides free and something below: the water slides to one of them and levels its surface |
| Free fall | `???`<br>`?X?`<br>`?_?` | `???`<br>`?_?`<br>`?X?` | free space below, the particle drops one row |
| Slide both | `???`<br>`?X?`<br>`_X_` | `???`<br>`?_?`<br>`x?x` | something below, both diagonals free: one of them, half of the time each |
| Slide left | `???`<br>`?X?`<br>`_XX` | `???`<br>`?_?`<br>`X??` | only the left diagonal is free |
| Slide right | `???`<br>`?X?`<br>`XX_` | `???`<br>`?_?`<br>`??X` | only the right diagonal is free |
| Stay | `???`<br>`?X?`<br>`XXX` | `???`<br>`?X?`<br>`???` | no free cell around (the border counts as solid), the particle stacks |

The bottom row of a result is the row below the particle, so `X??` means "moved
to the cell down-left". The rules of the water are the same drawing with only
one free side (`_AX`, `XA_`), so a grain of water can also flow alone.

The four rules of the slow sink are one mechanism seen from four sides. `Water
waits` is the clock: the drop under a grain walks its band instead of moving, and
the end of the band is the end of the wait. `Sand sinks` and `Sand sinks straight`
are the swap, drawn with `p` and `P`, and `Water forgets` restarts a drop that was
left at the end of its band with no grain on top.

A swap is written whole by a single rule: the grain goes to the cell of the drop
and the drop takes the cell the grain leaves, both from the same picture of the
world. That is what `p` and `P` draw, and it is why the rule leaves its own cell at
`?`: the swap writes it. The cell that receives the grain still has its turn ahead,
and its own rule would write over what is left there; it does not take that turn,
because the sweep leaves alone any cell that already has something in the next
generation. That works because a particle is only ever written where there was
none: the cells the sweep has not read yet are empty in it, so whatever is there
was put there by a rule that reached the cell first. Before, that could not be
known, so the swap had to be noted in an array of its own, with the coordinates of
the particle that started it, and completed when the sweep reached the other cell.

The drop that climbs keeps the shade it had, because the swap moves the value it
already holds: the pond keeps its variety without a rule having to draw a new shade
for it.

The swap only looks down. The sweep writes from the top, so the cell below is
always one it has not read yet; the cell above is already resolved, and the guard
that keeps a swap from writing over a cell that was taken would cancel it; and to
the side the direction of the row is drawn every frame, so the swap would happen in
half of the frames and the grain would sink in fits and starts. The table checks it
when it loads: a `P` anywhere but in the row below the particle stops the program
at start.

`Water waits` goes before `Water forgets`, which matches any drop at the end of its
band and would start again the one that still has a grain on top. The whole group
goes before `Free fall`, next to the water, because a drop that is waiting is still
water: without a grain on top it is born again before it can move.

Measured with the harness, nine grains over a pond of 108 drops and 6000 steps:
60.08 steps per row (721 steps for twelve rows, 6.7 frames, against 1.3 in the
air), nine grains in and nine out, 108 drops in and 108 out, the nine grains at
the bottom and the pond as many rows higher as the volume of the grains. The pond
also comes out with all eight birth shades in use, between five and twenty drops
on each, and not one drop left in the waiting stretch, which is what `V` could not
do: with `V` the 108 drops ended up on the first value and the water looked flat.
The fire now sees the waiting drops too: they are water, with their `A`, so the
eight rules that put a flame out treat them like any other, which they did not do
when the wait was a mark of its own.

A grain cannot drift sideways while it sinks, and the engine does not let it try. A
sideways swap would put the grain and the drop in the same row, whose direction is
drawn every frame: on the frames where the drop is read first it resolves its own
cell, the swap finds the destination taken and cancels; on the others the swap
writes both halves and the sweep skips the drop. The grain would sink in fits and
starts, one frame yes and the next no. The vertical swap has no such problem: the
two particles are in different rows and the sweep always reads them in the same
order, the one above first.

The drop does not always climb into the cell the grain leaves. Before writing it,
the cell of the grain is asked for a free side, in this generation and in the next
one: when it has one, the drop leaves by it and the cell of the grain stays empty;
when it has none, the drop climbs. A grain that falls into water displaces a drop,
and a drop with room beside it slips away instead of being pushed up. Without that
escape the water climbed inside any one-cell column of sand up to the air: measured
with the harness, with the surface of the pond at row 20 of a 36-row window and a
jet of 101 grains, the highest drop reached row 1 without the escape and row 12
with it, which is the level the pond has risen with the sand inside.

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
  world takes `ENGINE_MULTIPLIER` steps per frame (`9` today) and the picture is
  rebuilt every frame (`FRAMES_PER_PAINT = 1`). Measured on the default canvas,
  the redraw costs 0.95 ms and each step of the world 0.11 ms, so the nine of a
  frame come to about 1 ms, out of the 16.6 ms a frame has at 60 FPS.