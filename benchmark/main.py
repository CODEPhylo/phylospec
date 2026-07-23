from pathlib import Path

from parsers import beast3, beastx


def main():
    script_path = Path(__file__).resolve().parent / "example.phylospec"

    beast3.run_file(script_path)
    beastx.run_file(script_path)


if __name__ == "__main__":
    main()
