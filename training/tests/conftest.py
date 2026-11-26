from pathlib import Path

import pytest

# The committed record that `TestSelfPlay` checks the JVM writer still writes: it pins the
# record format (docs/self-play-records.md) that training reads.
FIXTURE_RECORDS = Path(__file__).resolve().parents[2] / "engine/src/test/resources/selfplay"
FIXTURE_SIZE = 5
FIXTURE_GENERATION = 0


@pytest.fixture(scope="session")
def fixture_records() -> Path:
    return FIXTURE_RECORDS


@pytest.fixture(scope="session")
def fixture_record_path() -> Path:
    [path] = sorted((FIXTURE_RECORDS / str(FIXTURE_SIZE) / str(FIXTURE_GENERATION)).glob("*.json"))
    return path
