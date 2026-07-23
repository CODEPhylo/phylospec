import subprocess
from pathlib import Path


def run_file(path: Path) -> None:
    script_path = path.resolve()
    jar_path = Path(__file__).resolve().parent.parent / "jars" / "beastx.jar"

    subprocess.run(
        ["java", "-jar", str(jar_path), script_path.name],
        cwd=script_path.parent,
        check=True,
    )
