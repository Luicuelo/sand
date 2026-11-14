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
| `src/principal` | `principal.MaterialSimulator` | The materials simulator: six materials, a clock per material, hit forces |
| `src/sand` | `sand.Sand` | The original falling sand, recovered from commit `a2679ca` and left as it was |

The design of the engine (materials, glyphs, rules and the reason behind each one)
has its own document: **[ENGINE.md](ENGINE.md)**, written in Spanish.

## Features

- Real-time falling sand particle simulation
- Six materials (sand, water, wood, fire, smoke, acid), each with its own band of
  palette values: the colour of a particle is its age
- Sand that sinks in water, slowly: the drop that holds a grain waits and then
  the two swap places, and a pile under water rolls down the diagonals into a cone
  instead of standing as a column
- An acid that is a heavy liquid, at half the pace of the water, and that etches
  the wood it touches, spending itself one bite per cell, that sinks in water to
  reach the wood at the bottom of a pond —spread out while it goes down, not in
  columns of one cell, rolling down to the diagonals when it piles up and sliding
  with the water of its own row until the pool is flat— and that water neutralises
  slowly, one value of charge at a time
- Interactive mouse controls to add particles
- A material selector, by button or by key, to paint with any of them, plus two
  more brushes: one that paints nothing, to move the mouse over the world
  without drawing, and an eraser that empties the cells under it
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
   - One entry per material: glyph, size of its band of values, clock, age and colours
   - Lays out the bands from value 1 in the order of the list
   - Holds the value to glyph table the rules ask for
   - Holds the clock of each material: how many steps of the world pass between two steps
   - Holds the age of each material: how many of those steps one value of its band takes
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
| `S` `A` `M` `F` `H` `C` | a value of that material | a new particle of that material |
| `s` `a` `m` `f` `h` `c` | the last value of that material: it has spent its life (for the water, it has waited long enough; for the acid, it has nothing left to bite, and the next rule turns it into water) | not used |
| `x` | same as `X` | one of these cells keeps the particle, drawn by chance |
| `o` | same as `X` | each of these cells rolls its own dice, so several can win: the winners are new particles that share the life the original has left, and nothing is born when there is nothing left to share |
| `V` | same as `X` | the particle one value older, wrapping back at the end |
| `W` | same as `X` | the particle one value older, staying at the last one, and only on the turns its age is due |
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

| Glyph | Material | Values | Born in | Clock | Age | Life | Reach | Colours |
|-------|----------|--------|---------|-------|-----|------|-------|---------|
| `S` | sand | 1-10 | 1-10 | 12 | 1 | no | no | pale yellow to golden ochre |
| `A` | water | 11-67 | 11-18 | 1 | 1 | no: the tail of the band is the wait, 49-56 steps | no | mid blue to light blue, and the wait flat |
| `M` | wood | 68-83 | 68-71 | 22 | 1 | 13-16 turns | never moves | light wood to dark wood |
| `F` | fire | 84-93 | 84-86 | 22 | 3 | 24-30 turns | 24-30 rows, a third of the screen | pale yellow to orange to red |
| `H` | smoke | 94-101 | 94-95 | 9 | 13 | 91-104 turns | the 75 rows of the canvas | dark grey to light grey |
| `C` | acid | 102-107 | 102-103 | 2 | 60 | 5-6 bites, and 600 steps while water eats it | a few cells of wood, one cell of the pond every 600 steps | pale green to deep green |

The last two columns are the same number read twice, and both come out of the
first four:

- **Life** is the values of the band times the age, counted in turns, minus what
  a particle spends being born: the spread is life already gone, which is why the
  fire starts up to two values late and the smoke one.
- **Reach** is that life counted in rows instead of turns, because climbing costs
  a turn per row. A particle with an age of one spends a value on every row it
  climbs, so its reach is its size; with an age of thirteen it climbs thirteen
  rows for every value it spends, and eight values carry it across the canvas.

That is the whole decoupling: the band is no longer the height, the age is what
turns values into turns, and a material can be made short and slow without
touching a single rule. The six bands take 107 of the 255 values of the palette,
so there is room for three or four more before a new one has to divide a band.

Every material is created at a random value among the first ones of its band
(`spread` in `Material.LIST`), so a pile is not a flat sheet of a single colour.
The materials with life spread only over the first few values, because there the
value is the age and the rest is life spent at birth. A material with life then
walks towards the last value: that is what `V` and `W` do, and `W` only spends a
value on the turns the age is due. The last value of a material with life is
painted almost white, so a particle that has spent its life is practically
invisible while it waits for the rule that transforms it.

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

The acid is the band with a third job. Its values are a **charge** and not a
clock: the drop is spent by what it does, one value per cell of wood it eats and
one every sixty turns —a hundred and twenty steps, because its clock is two—
while water dilutes it, and falling, flowing or going down through the water
costs nothing, so a pool of acid in the open is stable for ever and a drop is
worth five or six cells at most.

Its age of sixty is not a life, it is the patience of the water. The two
reactions are paid at two speeds, and that is the whole reason the acid uses two
life glyphs: the corroding rules write `r`, which charges the bite at once, and
the diluting ones write `W`, which waits for the age. A drop that bites spends a
value per turn and a drop that water is eating spends one every sixty turns, so
a value is a hundred and twenty steps and the whole drop six hundred steps. The
sixty and not a hundred and twenty comes out of the clock: the age is counted in
turns of the material, so when the clock went to two the age went to half, and
the chemistry keeps the pace in steps it had. Neutralising is therefore a
process and not a step: measured, a pool of 30 of acid at the bottom of 570 of
water starts paying around step 480, its outer layer goes at 600, and the whole
pool is water between 1,200 and 1,800, with 570 + 30 = 600 drops coming out.

Two more things make the acid usable where it is meant to be used. It is heavier
than the water, so it changes places with the drop below and goes down to the
bottom of a pond instead of resting on the surface; and its corroding rules go
before its diluting ones, so a drop that reaches the wood at the bottom of a pond
bites it instead of dissolving on the way. Without the first it floated and never
met the wood; without the second it stopped biting the moment it touched water.

### Clocks

There are two counters and each one has a job. The **frame counter** counts
pictures: the hit and the drawing divide it. The **step counter** counts turns of
the world: the materials and the brush divide it. `ENGINE_MULTIPLIER` is how many
steps the world takes per frame, and it is the knob that makes everything faster
or slower at once, without touching a single rule:

- the **water** has a clock of 1, so it steps on every step (540 a second);
- the **sand** has a clock of 12, so it steps one step in twelve (45 a second): it
  falls and spreads at a twelfth of the speed of the liquid;
- the **wood** and the **fire** have a clock of 22, and they share it on purpose:
  a flame climbs one row for every value the wood spends, so the burn front stays
  together; the **smoke** has a clock of 9;
- the **acid** has a clock of 2, alone among the liquids: it moves and bites at
  half the pace of the water, which is what makes it read as a heavy liquid and
  not as a second water, and its age of 60 is what keeps its chemistry at the
  pace it had, one value of dilution every 120 steps;
- the ones with life are stepped once every age turns, and each turn is one row
  they climb or one value they spend: the fire ages more than four times faster
  than the smoke, and the smoke reaches the ceiling with a band of eight.

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
one grain every twenty-four steps (every two frames and two thirds), the water
one every two (four and a half per frame) and the acid one every four (two and a
quarter). The turn in
between is what leaves the grain room to fall: painted on every turn, a grain is
born on the cell the one before has just left, the previous generation still
shows it there, so it slides sideways in the air and the jet opens into a cone.
With the gap the jet is one cell wide instead of three. The same gap is why the
wood and the fire, with a clock of 22, are painted one particle every 44 steps:
they are solids, they do not fall, so the holes left while dragging the mouse
close on their own as the brush passes again.

The sweep swaps sides **on every frame, not on every step**, so what matters is in
which frames the turns of a material land: a grain is due one step in twelve and a
frame has nine, so its turns fall in frames 0, 1, 2, 4, 5, 6, 8, 9 and so on, and
they see both directions. Were the turns always on the same side, every one of them
would read its rows in the same direction and push its pile sideways: alternating
per step, or with an even quotient between clock and multiplier, a block of sand
ends up 5.4 cells to one side and visibly flatter (13 rows instead of 17 and 42
columns instead of 37). Materials faster than the frame (the water, with 1, and
the acid, with 2) are stepped several times per frame and see both sides anyway;
the smoke, with 9,
lands on every frame and sees both too, and the wood and the fire, with 22, land
one turn every two frames and a half (frames 2, 4, 7, 9, 12...), so they also
take both directions.

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
cell of the flame it has just put out, among them), then the free fall, then the
acid, which sinks, bites, rolls and levels before it lets the water eat it, and at
the end the diagonals and the stacking, the net every particle obeys.

| Rule | Match | Result | When |
|------|-------|--------|------|
| Fire dies under water | water on any of its eight sides | `?_?` | the flame touches water: it is consumed where it is instead of rising or spreading, which is what stops a fire from jumping over a drop |
| Fire ends | `f` | `H` | the flame has spent its life and becomes smoke |
| Smoke ends | `h` | `_` | the smoke has spent its life and disappears |
| Wood ends | `m` | `F` | the wood has spent its life and catches fire |
| Wood burns | fire on any of its four sides | `?W?` | the wood walks towards its end, one value per turn |
| Fire feeds | `???`<br>`?F?`<br>`?M?` | `???`<br>`?l?`<br>`???` | there is wood below: the flame recovers half of the life it has spent, which is what keeps it alive while there is fuel |
| Wood stays | `M` | `X` | wood never falls, and it keeps its value instead of being born again |
| Fire rises | `?_?`<br>`?F?`<br>`???` | `?W?`<br>`?_?`<br>`???` | free space above: the flame rises one row |
| Fire spreads | `???`<br>`?F?`<br>`???` | `?o?`<br>`oWo`<br>`?o?` | the flame cannot rise: it ages in place and copies itself to the four cells around it, one in four each, giving every copy a quarter of the life it has left |
| Smoke rises | `?_?`<br>`?H?`<br>`???` | `?W?`<br>`?_?`<br>`???` | smoke rises like fire, but it neither spreads nor creates anything |
| Smoke ages | `?H?` | `?W?` | the smoke cannot rise: it ages where it is, so smoke trapped under the sand still fades out and disappears |
| Acid ends | `c` | `A` | the drop has spent its charge and becomes water: it is neutralised whether water ate it or it ate the wood. It has to go before the diluting and the corroding rules, because `W` and `r` cannot pass the end of the band |
| Acid sinks sideways | `???`<br>`?C?`<br>`AAA` | `???`<br>`???`<br>`ppp` | with water in the three cells below the drop goes down to one of them, drawn at equal parts, so a pour that crosses a pond spreads while it goes down instead of cutting it in columns of one cell. It is the same drawing the grain of sand uses to sink, and it goes before the straight one; when the diagonals are not water it does not match and the drop goes straight down |
| Acid sinks | `???`<br>`?C?`<br>`?A?` | `???`<br>`???`<br>`?P?` | the acid is heavier than the water: it changes places with the drop below and goes down one row, so a pour reaches the wood at the bottom of a pond. It goes after `Free fall` (a free cell is still a free cell) and after the sideways one, and before everything else the drop does |
| Acid corrodes | `???`<br>`?C?`<br>`?M?` | `???`<br>`?r?`<br>`?H?` | wood on any of the four sides: the wood leaves smoke and the drop pays a bite at once, `r`, without waiting for its age. The rules are centred on the acid, not on the wood, because whoever pays is the one in the middle |
| Acid rolls | `???`<br>`?C?`<br>`ACA` | `???`<br>`???`<br>`p?p` | another drop of acid below: the same roll as the grain of sand, so a pool inside a pond spreads instead of standing on end. It goes after the corroding rules, because a drop that can bite has to bite first, and before the diluting ones, which claim the cell of the drop even on the turns its age is not due: with the other order a drop surrounded by water would stay still for ever |
| Acid levels | `???`<br>`ACA`<br>`?C?` | `???`<br>`p?p`<br>`???` | another drop of acid below and water at the side: the drop changes places with that water and stays in its row, so the pile spreads flat instead of holding the forty-five degrees of a cone. It asks for acid below, so a drop that has water below goes down with the sinking rules, which go first, and the last row of a pool, with the floor below it, never matches. When the drop slides out of the footprint of the row underneath, what it has below is water and the sinking takes it down: that is what flattens the surface |
| Acid dilutes | `?A?`<br>`?C?` | `???`<br>`?W?` | water on any of the four sides: the water eats the acid and leaves it a value of its charge, but only on the turns its age is due, so the drop loses one value every sixty turns —a hundred and twenty steps—. This is what makes the neutralisation a process and not a step. They go below the corroding rules, so a drop surrounded by water still bites the wood it has at its side |
| Acid flows | `_C_`<br>`?X?` | `x_x` | the same levelling as the water, one of the two sides drawn by chance |
| Sand falls through acid | `???`<br>`?S?`<br>`?C?` | `???`<br>`???`<br>`?P?` | a grain over a drop changes places with it instead of resting on it: the acid has no band that could hold it |
| Water puts out | `?F?`<br>`?A?` | `?X?`<br>`?_?` | the drop jumps into the cell the flame has just left, so the water follows the fire it has put out. Only upwards: the flame above is stepped first, so its cell is already free, while below or at the side the drop would move first and the flame would write over it |
| Water waits | `?S?`<br>`?A?`<br>`???` | `???`<br>`?W?`<br>`???` | a grain rests on the drop: the drop walks one value towards the end of its band, and that walk is the wait. Nothing is moved and the stretch of the band is one flat colour, so the pond does not change while a grain sits on it |
| Water forgets | `?a?` | `?A?` | no grain on top: the sand slid away, so the drop is born again with one of the shades of a new drop instead of keeping the flat shade of the wait for ever |
| Sand sinks | `???`<br>`?S?`<br>`AaA` | `???`<br>`???`<br>`ppp` | the drop below has reached the end of its band: the grain changes places with one of the three drops under it, drawn at random, one in three each |
| Sand sinks straight | `???`<br>`?S?`<br>`?a?` | `???`<br>`???`<br>`?P?` | the same when the two diagonals are not water, at the wall of the pool or on a pile: the grain changes places with the drop below, without choosing |
| Sand rolls | `???`<br>`?S?`<br>`ASA` | `???`<br>`???`<br>`p?p` | another grain below: the diagonal slides ask for free space and inside the water there is water, so without this rule the table ended in `Stay` and a pile under water grew as a column. The grain changes places with the drop at the diagonal and goes down one row; with both diagonals water the side is drawn, with one there is nothing to draw, and a free diagonal still wins because the slides go later |
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

The swap only looks down or to its own row. The cell below is always one the sweep
has not read yet; the cell above never, because it is already resolved. The cell at
the side may have been read or not, depending on the direction of the frame, and it
is read with the same criterion as the material letters: a cell the sweep has not
reached has to be free, and a cell it has read has to still hold its drop, whatever
value the drop carries. That is what keeps a swap with a drop that flowed away in
this same step from copying the drop that left, and what lets the acid level
sideways in both directions of the sweep instead of in fits and starts. When the
cell had already written itself, the value that moves is the one of the next
generation, so a drop that is walking its band does not start again. The table
checks the direction when it loads: a `P` above the row of the particle stops the
program at start.

No rule of the sand swaps in its own row: a grain goes down one row for every drop
it changes places with, and that is the wait.

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

A grain cannot drift sideways while it sinks. A sideways swap would put the grain
and the drop in the same row, whose direction is drawn every frame, and even with
the criterion above the grain would go down a row every other frame; the vertical
swap has no such problem, because the two particles are in different rows and the
sweep always reads them in the same order, the one above first.

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

Under water the diagonals are not free, so neither the slides nor the levelling
can touch a pile: what is at the diagonal is a drop and not a hole. A grain that
lands on another grain has nowhere to go below —sand, not a waiting drop— and
nowhere to the sides, and the table ended in `Stay`, so a column of sand inside a
pond grew as a column. The three rolling rules are the same three drawings of the
slides read from the water: if the diagonal is a drop, the grain and the drop
change places and the grain goes down one row. They go after the two sinkings,
which are for the grain that lands on water, and before `Free fall`; a free
diagonal still wins because the slides come later and cost no water.

Measured with the harness, eighty grains poured in a column of four by twenty into
a pond of 2,924 drops: by step 2,400 they occupy between 29 and 39 columns and 4
or 5 rows, against 10 columns and 22 rows without the roll. The count does not
move (the peak is the same 3,082 particles it started with) and the wait keeps its
price: a grain alone over the pond still goes down one row every 60 steps, and the
roll does not restart the wait. Dry sand does not notice: the same column on dry
ground ends in 19 columns and 8 rows with the rules and without them, because
without water at the diagonal they do not match.

The acid pays for what it does, at two speeds. The corroding and the diluting
rules are centred on the acid and not on the wood or the water, and that is not a
detail: the glyphs that walk a life always write the particle being swept, so an
`r` or a `W` in the cell of a neighbour would move the neighbour instead of
charging it. Whoever pays is the one in the middle, and the smoke goes in the cell
of the wood as a material glyph away from home, which takes the place of the
particle that is already there.

The end of the charge is water, and that is what the one-step neutralisation
could not afford: a drop that turned into water on contact touched the acid
around it, the dilution ruled and the pool went in a handful of frames. Paid, the
end is one drop and the water it leaves has to eat the next, so a pool survives
the first death and dissolves from the surface. A pour of two hundred drops on a
log of 440 cells eats 38 cells in four hundred steps and 122 by step 2,400, and
by step 4,800 not one drop of acid is left: every drop of it is water. The water
appears only where the acid is spent, and the acid limits itself.

Measured with the harness: a block of wood under a rain of acid keeps the count of
particles (160 in, 160 out) and every cell eaten leaves a smoke; a layer of twenty
drops, in ten columns, over a pond of 2,924 drops reaches the bottom in about 110
steps, one row every two, without losing a particle and without touching the
water, and lands spread out over 27 columns instead of the ten of the layer it
started in; 150 of acid
over 300 of water and a plank of 300 eat nothing by step 20, because the pool is
still going down, 42 cells by step 120, 111 by 600, 131 by 1,200 and 135 by step
2,400; a grain of sand changes places with the drop instead of floating, reaching
the bottom of a pool of 600 drops without spending one; thirty drops of acid at
the bottom of 570 of water start paying around step 480 and are water between step
1,200 and 1,800, with 600 drops out; and the pour on the log stops two to seven
layers down, because the smoke of a bite has nowhere to go under the acid and
waits in the cell it was born in.

The pool had the same problem as the pile: with water at both sides the levelling
of the acid found no free cell either, so the drops that landed on other drops
stayed there and a pour inside a pond stood on end. `Acid rolls` is the same three
drawings as the sand, and it goes before the diluting rules for a reason the
drawing does not show: the dilution claims the cell of the drop even on the turns
its age is not due, so placed before, a drop surrounded by water would stay still
for ever and the pool would never spread. Measured with the harness, eighty drops
poured in a column of four by twenty into a pond of 2,924 drops: by step 120 they
occupy 24 columns and 7 rows, against 16 columns and 20 rows without the roll,
and they spread before they dissolve.

Rolling down to the diagonals is what the grain does, and a grain keeps the
forty-five degrees of a cone: in a cone every drop of a row has another drop on
the diagonal below it, so the roll always finds somewhere to go and the pile
grows as high as it is wide. A liquid was still missing the other half, the one
that moves a drop to the side of its own row: `Acid levels`, three drawings that
change the drop with the water beside it when what it has below is another drop.
The drawing asks for a `p` in the row of the particle, which is the first swap of
the engine that does not look down, and takes the water of that row with the
same dice as the rolls. A free cell would not do: inside a pond the cell beside
the drop holds water, so the only way to the side is a change of places.

Measured with the harness, forty drops poured into a pond of 4,500 drops, one
every four steps: by step 600 they occupy 37 columns and 2 rows, against 28
columns and 4 rows without the rule; by step 900 they are a single row of 37
columns; by 1,200 there is one drop left and by 1,600 none are left, against 18
and 8 of the cone. The count of particles is the same 4,540 at every measure, so
nothing is created or lost on the way. The warning is the other side of the same
coin: a sheet one cell thick puts every drop against the water, so the pool pays
for the flat surface by dissolving sooner than the cone, which kept its inside
dry. That is a matter of the age of the acid and not of the levelling, and the
age is a single number in `Material.LIST`.

Hit forces use the same language with two more rules: `?X_` → `?_X` when the
particle is pushed right and `_X?` → `X_?` when it is pushed left. The wood is
the exception, and it is not a rule: a hit is a gesture of the mouse and not a
turn of the world, so its pass does not read the table at all. `Material.ANCHORED`
says which materials a hit cannot move, and the wood is put in place and stays
there.

### Hit Forces

The simulation supports applying forces to particles with mouse clicks:
- Left-click applies a force towards the left
- Right-click applies a force towards the right
- Force strength is randomized between 1 and 10 (`MaterialRules.MAX_HIT_FORCE`)
- Probability of force application is proportional to hitForce value (10% to 100%)
- A particle is only pushed when the cell on that side is free, so a hit never
  squeezes particles into each other
- The wood is not pushed: a hit moves what is loose, so a plank stays where it was
  placed and nothing can be pushed through it

## Technical Implementation

### Rendering Optimization

- Uses block-based rendering (8x8 pixel blocks per cell) for performance
- Implements double buffering with int arrays for efficient state updates
- Uses WritableImage and PixelWriter for direct pixel manipulation
- Employs BGRA byte array for fast block filling

### Mouse Interaction

- Mouse movement paints what the selected brush says at the cursor position: a
  particle of the material, nothing at all, or empty space. A material is born at
  half its pace, so each particle is born over free space and the jet falls as a
  column; the eraser has no pace, because it removes instead of adding
- Holding Space widens the brush to a square of two cells of radius, so the
  eraser too clears a whole area
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

- **Mouse Movement**: Paint with the selected brush at the cursor position
- **Material Buttons**: Pick the material the mouse paints (the title shows the
  glyph of the chosen one)
- **None Button**: Paint nothing, to move the mouse over the world without
  drawing anything
- **Erase Button**: Empty the cells under the mouse, the same as writing `_` in a
  rule; with Space held it empties a square around it
- **Material Keys**: `S` sand, `A` water, `M` wood, `F` fire, `H` smoke, `C` acid:
  the same as pressing the button
- **Escape**: Pick the brush that paints nothing, the same as pressing None
- **Delete**: Pick the eraser, the same as pressing Erase
- **Hold Space**: Paint or erase a wider area around the mouse
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