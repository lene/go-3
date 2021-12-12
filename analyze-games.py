import re
from argparse import ArgumentParser
from collections import defaultdict
from dataclasses import dataclass
from operator import itemgetter
from pathlib import Path
from typing import List, Dict, Any, Callable

import pandas as pd

ResultsTable = Dict[str, Dict[str, pd.DataFrame]]


@dataclass
class Combination:
    algorithm_black: str
    algorithm_white: str
    size: int

    def __str__(self):
        return f"{self.algorithm_black}:{self.algorithm_white}:{self.size}"

    @classmethod
    def from_dir(cls, base_dir: Path) -> List['Combination']:
        return [
            Combination(match(file)['black'], match(file)['white'], int(match(file)['size']))
            for file in (base_dir.glob("*:*:*.csv"))
        ]


def match(path: Path):
    return re.match(
        r'\w*/(?P<black>[\w,]+):(?P<white>[\w,]+):(?P<size>\d+).csv', str(path)
    ).groupdict()


def table(basedir: Path) -> ResultsTable:
    result = defaultdict(dict)
    for combination in Combination.from_dir(basedir):
        result[combination.algorithm_black][combination.algorithm_white] = pd.read_csv(
            str(basedir / f"{combination}.csv"), names=['black', 'white']
        )
    return result


def count(frame: pd.DataFrame) -> int:
    return frame.shape[0]


def wins(frame: pd.DataFrame) -> str:
    return f"{0 if frame.size == 0 else frame[frame['black'] > frame['white']].size/frame.size*100:.1f}%"


def score(frame: pd.DataFrame) -> float:
    return (frame['black'] - frame['white']).mean()


def evaluate(
        results: ResultsTable, function: Callable[[pd.DataFrame], Any]
) -> Dict[str, Dict[str, Any]]:
    return {
        alg_black: {
            alg_white: function(frame)
            for alg_white, frame in res_black.items()
        } for alg_black, res_black in results.items()
    }


def abbreviate(word: str) -> str:
    return word[0] + ''.join([c for c in word if c.isupper() or not c.isalnum()])


def print_table(tbl: Dict[str, Dict[str, Any]]) -> None:
    tbl = {
        abbreviate(black): {
            abbreviate(white): values2
            for white, values2 in sorted(values.items(), key=itemgetter(0))
        } for black, values in tbl.items()
    }
    with pd.option_context('display.float_format', '{:,.2f}'.format):
        print(pd.DataFrame.from_dict(tbl, orient='index').fillna(' -- '))


def main() -> None:
    parser = ArgumentParser()
    parser.add_argument(
        '-d', '--base-dir', type=str, default='results', help='Dir storing the results'
    )
    args = parser.parse_args()
    results = table(Path(args.base_dir))
    print("number of games:")
    print_table(evaluate(results, count))
    print("percentage of black wins:")
    print_table(evaluate(results, wins))
    print("average black score advantage:")
    print_table(evaluate(results, score))


if __name__ == '__main__':
    main()
