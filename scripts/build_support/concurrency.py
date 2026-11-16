"""构建入口的 worker 额度与 Gradle 参数。"""
import argparse

DEFAULT_JOBS = 12
MAX_JOBS = 64


def parse_jobs(value: str) -> int:
    try:
        jobs = int(value)
    except ValueError:
        raise argparse.ArgumentTypeError("jobs must be an integer from 1 to 64") from None
    if not 1 <= jobs <= MAX_JOBS:
        raise argparse.ArgumentTypeError("jobs must be an integer from 1 to 64")
    return jobs


def gradle_worker_args(jobs: int) -> list[str]:
    return [f"--max-workers={jobs}"]
