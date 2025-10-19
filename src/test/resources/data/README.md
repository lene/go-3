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

## Adding New Test Data

When adding new game data files for bug analysis:
1. Name the file with the game ID (e.g., `GAME_ID.json`)
2. Document it in this README with:
   - Source (bug number or description)
   - Board size
   - Number of moves
   - Investigation results
3. Use `AnalyzeGameData` tool to replay and analyze the game
