import os
from pathlib import Path

from dotenv import load_dotenv


PROJECT_ROOT = Path(__file__).resolve().parents[1]
load_dotenv(PROJECT_ROOT / ".env")


def require_env(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise RuntimeError(f"Required environment variable {name} is not set")
    return value


def mysql_connection_config() -> dict:
    return {
        "host": os.getenv("MYSQL_HOST", "localhost"),
        "port": int(os.getenv("MYSQL_PORT", "3306")),
        "user": require_env("SPRING_DATASOURCE_USERNAME"),
        "password": require_env("SPRING_DATASOURCE_PASSWORD"),
        "database": os.getenv("MYSQL_DATABASE", "ielts_smartprep"),
    }
