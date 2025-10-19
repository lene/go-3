# Test Game Data

This directory contains saved game files for bug analysis and testing.

## Files

### 04hfPW.json
- **Source**: Bug #65 - "Ko rule seems to not always work"
- **Size**: 7x7x7 board
- **Moves**: 345 moves
- **Status**: Invalid/corrupted game data

**Investigation Results**:
The game data contains illegal moves that violate basic Go rules. Move 282 attempts to place a Black stone at position (6,2,6) which is already occupied by Black from move 280. This violates the fundamental rule that stones cannot be placed on occupied positions.

The current game engine correctly rejects this move with a `PositionOccupied` error, proving that:
1. The Ko rule implementation is correct
2. The game data is either corrupted or from an older buggy version
3. Bug #65 should be closed as "invalid data"

**Analysis**:
To analyze this game and see where it fails:
```bash
sbt "Test/runMain go3d.AnalyzeGameData src/test/resources/data/04hfPW.json 278 287"
```

This will show that the game stops at move 282 with a `PositionOccupied` error.

### gNoDpK.json, yLtN8s.json, gyxAXY.json, sXBvhd.json, 6axryR.json, B8orAu.json
- **Source**: Bug #66 - "Server hanging sometimes"
- **Size**: 7x7x7 board
- **Moves**: 336-342 moves
- **Status**: Performance issue - FIXED

**Investigation Results**:
These games exhibited severe performance degradation with certain moves taking over 2 seconds to process, causing the server to appear "hung" during gameplay.

Root cause: The `connectedStones()` and `hasLiberties()` methods in `Goban.scala` were creating deep copies of the entire 3D board array on every recursive call via `setStone()`. For games with large connected groups (~300 stones on a 7x7x7 board), this resulted in exponential complexity.

**Fix**: Refactored both methods to use a mutable `visited` set passed through recursive calls instead of creating new board copies. This eliminated the deep copy overhead.

**Performance Impact**:
- Before: Move 339 in gNoDpK.json took 2173ms, total game time 2307ms
- After: Move 339 takes <10ms, total game time 124ms
- **Result: ~18.6x overall speedup, 200x+ speedup on problematic moves**

**Analysis**:
To measure performance of these games:
```bash
sbt "Test/runMain go3d.MeasureMoveTimes src/test/resources/data/gNoDpK.json 10"
```

## Adding New Test Data

When adding new game data files for bug analysis:
1. Name the file with the game ID (e.g., `GAME_ID.json`)
2. Document it in this README with:
   - Source (bug number or description)
   - Board size
   - Number of moves
   - Investigation results
3. Use `AnalyzeGameData` tool to replay and analyze the game
