package principal;

/**
 * Defines transition rules for falling sand particle simulation.
 * <p>
 * This class implements the physics rules that govern how particles move and
 * interact in the simulation. It evaluates gravity, collision, and stacking
 * behaviors based on neighbor patterns. The class uses pattern matching to
 * determine how each particle should behave in the next simulation step.
 * </p>
 * <p>
 * The physics simulation includes:
 * <ul>
 *   <li>Gravity - particles fall downward when possible</li>
 *   <li>Collision detection - particles interact with other particles and boundaries</li>
 *   <li>Stacking - particles form piles when they can't fall further</li>
 *   <li>Sliding - particles slide diagonally when direct downward movement is blocked</li>
 *   <li>Hit forces - particles can be moved by user-applied forces</li>
 * </ul>
 * </p>
 * 
 * @see Sand
 * @see Renderer
 */
class Transition {
	private final int width;
	private final int height;
	private final Pattern patterns = new Pattern();

	/**
	 * Pattern matching class for detecting particle configurations.
	 * <p>
	 * This inner class handles pattern matching for different particle arrangements.
	 * Each pattern is a 3x3 matrix where:
	 * <ul>
	 *   <li>-1 = don't care (can be anything)</li>
	 *   <li>1 = particle present</li>
	 *   <li>0 = empty/void</li>
	 * </ul>
	 * </p>
	 * <p>
	 * The center of the matrix (index 4) represents the particle being evaluated,
	 * while the surrounding indices represent its neighbors.
	 * </p>
	 */
	private class Pattern {
		// State values:
		// -1 = don't care (can be anything)
		// 1 = particle present
		// 0 = empty/void
		private static final int NUMBER_PATTERNS = 7;
		int[][] matrix = new int[NUMBER_PATTERNS][9];

		/**
		 * Assigns a pattern to a specific pattern number.
		 * 
		 * @param numberOfPatter the pattern number to assign to
		 * @param imatrix        the pattern definition as an array of 9 integers
		 */
		public void assignPattern(int numberOfPatter, int[] imatrix) {
			matrix[numberOfPatter] = imatrix;
		}

		/**
		 * Checks if a specific pattern matches at a given position in the cell data.
		 * 
		 * @param numberOfPatter the pattern number to check
		 * @param x              the x coordinate of the cell to check
		 * @param y              the y coordinate of the cell to check
		 * @param width          the width of the simulation grid
		 * @param height         the height of the simulation grid
		 * @param cellData       the cell data to check against
		 * @return true if the pattern matches, false otherwise
		 */
		public boolean checkPattern(int numberOfPatter, int x, int y, int width, int height, int[][] cellData) {

			boolean match = true;

			for (int b = -1; b <= 1 && match; b++) {
				for (int a = -1; a <= 1 && match; a++) {
					int matrixValue = matrix[numberOfPatter][(b + 1) * 3 + (a + 1)];
					if (matrixValue == -1)
						continue;

					int value = 0;
					int xv = x + a;
					int yv = y + b;
					if (yv >= height)
						value = 1; // bottom border solid
					if (xv < 0 || xv >= width)
						value = 1; // lateral borders solid

					if (xv >= 0 && yv >= 0 && xv < width && yv < height) {
						value = cellData[yv][xv] == 0 ? 0 : 1;
					}
					if (value != matrixValue) {
						match = false;
					}
				}
			}

			return match;
		}

	}

	/**
	 * Constructs a new Transition object with the specified dimensions.
	 * 
	 * @param width  the width of the simulation grid
	 * @param height the height of the simulation grid
	 */
	public Transition(int width, int height) {
		this.width = width;
		this.height = height;

		// Pattern 0: Free fall - particle can fall straight down
		// [ ? ][ ? ][ ? ]
		// [ ? ][###][ ? ]  ### = current particle
		// [ ? ][ 0 ][ ? ]  0 = empty space, ? = don't care
		patterns.assignPattern(0, new int[] { -1, -1, -1, -1, 1, -1, -1, 0, -1 });

		// Pattern 1: Slide left-down - blocked below, can slide diagonally left
		// [ ? ][ ? ][ ? ]
		// [ ? ][###][ ? ]
		// [ 0 ][###][###]  Left-down is empty, others blocked
		patterns.assignPattern(1, new int[] { -1, -1, -1, -1, 1, -1, 0, 1, 1 });

		// Pattern 2: Slide right-down - blocked below, can slide diagonally right
		// [ ? ][ ? ][ ? ]
		// [ ? ][###][ ? ]
		// [###][###][ 0 ]  Right-down is empty, others blocked
		patterns.assignPattern(2, new int[] { -1, -1, -1, -1, 1, -1, 1, 1, 0 });

		// Pattern 3: Completely blocked - particle cannot move anywhere
		// [ ? ][ ? ][ ? ]
		// [ ? ][###][ ? ]
		// [###][###][###]  All positions below are blocked
		patterns.assignPattern(3, new int[] { -1, -1, -1, -1, 1, -1, 1, 1, 1 });

		// Pattern 4: General case - particle with something below (for further checks)
		// [ ? ][ ? ][ ? ]
		// [ ? ][###][ ? ]
		// [ ? ][###][ ? ]  Something directly below
		patterns.assignPattern(4, new int[] { -1, -1, -1, -1, 1, -1, -1, 1, -1 });
		
		// Pattern 5: Can move right - used for hit forces
		// [ ? ][ ? ][ ? ]
		// [ ? ][###][ 0 ]  Right side is empty
		// [ ? ][ ? ][ ? ]
		patterns.assignPattern(5, new int[] { -1, -1, -1, -1, 1, 0, -1, -1, -1 });
		
		// Pattern 6: Can move left - used for hit forces
		// [ ? ][ ? ][ ? ]
		// [ 0 ][###][ ? ]  Left side is empty
		// [ ? ][ ? ][ ? ]
		patterns.assignPattern(6, new int[] { -1, -1, -1, 0, 1, -1, -1, -1, -1 });
	}


    /**
     * Calculates the next state for a particle when a hit force is applied.
     * <p>
     * This method handles the physics when a user applies a force to the particles.
     * The probability that a particle is affected by the force is proportional to
     * the hitForce value (hitForce/10).
     * </p>
     * 
     * @param x          the x coordinate of the particle
     * @param y          the y coordinate of the particle
     * @param cellData   the current cell data
     * @param nextCellData the next cell data (to be updated)
     * @param hitForce   the strength of the hit (1-10)
     * @param hitDirection the direction of the hit (1 = right, -1 = left)
     */
    public void getNextHitState(int x, int y, int[][] cellData, int[][] nextCellData, int  hitForce, int hitDirection) {

        // Roll dice to determine if this particle is affected by the hit force
        // Higher hitForce = higher probability of being affected (hitForce/10 chance)
        int dice= (int) (Math.random()*10);

        // Check if particle should be affected by the hit force
        if (hitForce!=0&&hitDirection!=0&&dice < hitForce) {

            // Hit direction is RIGHT (1): try to move particle to the right
            if (hitDirection==1&&x+1<width) {
                // Check if right side is empty using pattern 5
                if (patterns.checkPattern(5, x, y, width, height, cellData)) {
                    nextCellData[y][x+1] = cellData[y][x];  // Move particle right
                    nextCellData[y][x] = 0;                 // Clear original position
                    return;
                }
            }
            // Hit direction is LEFT (-1): try to move particle to the left
            if (hitDirection==-1&&x-1>=0) {
                // Check if left side is empty using pattern 6
                if (patterns.checkPattern(6, x, y, width, height, cellData)) {
                    nextCellData[y][x-1] = cellData[y][x];  // Move particle left
                    nextCellData[y][x] = 0;                 // Clear original position
                    return;
                }
            }
        }
        // If particle wasn't moved by hit force, keep it in the same position
        if (nextCellData[y][x]== 0)  nextCellData[y][x]= cellData[y][x];
    }
    
    /**
     * Calculates the next state for a particle during normal simulation.
     * <p>
     * This method implements the standard falling sand physics:
     * <ul>
     *   <li>Particles fall due to gravity</li>
     *   <li>Stack when hitting bottom or other particles</li>
     *   <li>Slide laterally when direct fall is blocked</li>
     * </ul>
     * </p>
     * 
     * @param x          the x coordinate of the particle
     * @param y          the y coordinate of the particle
     * @param cellData   the current cell data
     * @param nextCellData the next cell data (to be updated)
     * @param color      the color of the particle (not directly used in physics)
     */
	public void getNextState(int x, int y, int[][] cellData, int[][] nextCellData, int color) {

		// PRIORITY 1: Check if particle can fall straight down (free fall)
		// Uses pattern 0: space directly below is empty
		if (patterns.checkPattern(0, x, y, width, height, cellData)) {
			if (y + 1 < height) {
				nextCellData[y + 1][x] = cellData[y][x];  // Move particle down
				nextCellData[y][x] = 0;                   // Clear original position
				return;
			}
		}

		// PRIORITY 2: Particle has something directly below - check sliding options
		// Uses pattern 4: something is directly below the particle
		if (patterns.checkPattern(4, x, y, width, height, cellData)) {

			// Check if particle is completely blocked (cannot move anywhere)
			// Uses pattern 3: all spaces below are occupied
			if (patterns.checkPattern(3, x, y, width, height, cellData)) {
				nextCellData[y][x] = cellData[y][x];  // Stay in same position
				return;
			}

			// Check if particle can slide diagonally left-down
			// Uses pattern 1: left-down is empty, others blocked
			if (patterns.checkPattern(1, x, y, width, height, cellData)) {
				if (y + 1 < height && x - 1 >= 0) {
					nextCellData[y + 1][x - 1] = cellData[y][x];  // Move diagonally left-down
					nextCellData[y][x] = 0;                       // Clear original position
					return;
				}

			}

			// Check if particle can slide diagonally right-down
			// Uses pattern 2: right-down is empty, others blocked
			if (patterns.checkPattern(2, x, y, width, height, cellData)) {
				if (y + 1 < height && x + 1 < width) {
					nextCellData[y + 1][x + 1] = cellData[y][x];  // Move diagonally right-down
					nextCellData[y][x] = 0;                       // Clear original position
					return;
				}

			}

			// FALLBACK: If both diagonal directions are available, choose randomly
			// This adds natural randomness to particle flow
			int random = Math.random() > 0.5 ? -1 : 1;
			if (random < 0 && x - 1 > 0) {
				nextCellData[y + 1][x - 1] = cellData[y][x];  // Move left-down
			} else
				nextCellData[y + 1][x + 1] = cellData[y][x];  // Move right-down
			nextCellData[y][x] = 0;                           // Clear original position

		}

	}

}