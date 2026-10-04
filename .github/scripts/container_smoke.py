"""Local container checks, not deployment evidence. No customer API/model calls."""
import argparse
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
import uuid


def docker(*arguments):
    result = subprocess.run(["docker", *arguments], capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("Docker operation failed; diagnostics withheld")
    return (result.stdout + (result.stderr if arguments[0] == "logs" else "")).strip()


def request(origin, path, expected):
    try:
        response = urllib.request.urlopen(origin + path, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read()
        if response.status != expected:
            raise AssertionError("Unexpected HTTP status; response withheld")
        return body


def wait_ready(origin, path):
    for _ in range(60):
        try:
            return request(origin, path, 200)
        except (OSError, AssertionError):
            time.sleep(1)
    raise RuntimeError("Container readiness timeout")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backend", required=True)
    parser.add_argument("--web", required=True)
    args = parser.parse_args()
    for key in ("POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD", "AUTH_ACCESS_TOKEN_SIGNING_KEY"):
        if not os.environ.get(key):
            raise RuntimeError("Required local configuration missing; values withheld")
    environment = os.environ.copy()
    environment.update(
        SPRING_PROFILES_ACTIVE="prod",
        DATABASE_URL="jdbc:postgresql://postgres:5432/" + environment["POSTGRES_DB"],
        DATABASE_USERNAME=environment["POSTGRES_USER"],
        DATABASE_PASSWORD=environment["POSTGRES_PASSWORD"],
        REDIS_HOST="redis",
        REDIS_PORT="6379",
        REDIS_PASSWORD="",
        AUTH_RATE_LIMIT_NAMESPACE="container-ci-" + uuid.uuid4().hex,
    )
    markers = [environment[k] for k in ("DATABASE_PASSWORD", "AUTH_ACCESS_TOKEN_SIGNING_KEY")]
    containers = []
    try:
        # Required secrets/configuration have no permissive production fallback.
        missing = docker("run", "-d", "--name", "ai-order-ci-missing-" + uuid.uuid4().hex, args.backend)
        containers.append(missing)
        for _ in range(60):
            state = json.loads(docker("inspect", "--format", "{{json .State}}", missing))
            if not state["Running"]:
                break
            time.sleep(1)
        assert not state["Running"] and state["ExitCode"] != 0
        assert not any(marker in docker("logs", missing) for marker in markers)
        print("PASS: backend without required configuration fails closed; secret markers absent")
        for image, port, user in ((args.backend, 8080, "10001"), (args.web, 3000, "1001")):
            arguments = ["docker", "run", "-d", "--name", "ai-order-ci-" + uuid.uuid4().hex,
                         "-p", "127.0.0.1::" + str(port)]
            if port == 8080:
                arguments += ["--network", "ai-order-delivery-agent_local"]
                for key in ("SPRING_PROFILES_ACTIVE", "DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD",
                            "AUTH_ACCESS_TOKEN_SIGNING_KEY", "REDIS_HOST", "REDIS_PORT", "REDIS_PASSWORD",
                            "AUTH_RATE_LIMIT_NAMESPACE"):
                    arguments += ["--env", key]
            result = subprocess.run(arguments + [image], env=environment, capture_output=True, text=True)
            if result.returncode:
                raise RuntimeError("Container launch failed; diagnostics withheld")
            container = result.stdout.strip()
            containers.append(container)
            origin = "http://" + docker("port", container, str(port) + "/tcp")
            if port == 8080:
                assert json.loads(wait_ready(origin, "/actuator/health/readiness")) == {"status": "UP"}
                assert json.loads(request(origin, "/actuator/health/liveness", 200)) == {"status": "UP"}
                request(origin, "/api/v1/integrations", 401)
                logs = docker("logs", container)
                assert "Successfully validated 7 migrations" in logs
            else:
                wait_ready(origin, "/")
                for path in ("/login", "/register", "/dashboard", "/integrations", "/chat"):
                    request(origin, path, 200)
            assert docker("exec", container, "id", "-u") == user
            assert not any(marker in docker("logs", container) for marker in markers)
            docker("exec", container, "sh", "-c", "test ! -e /app/.env && test ! -e /app/.env.local")
            docker("stop", "--time", "35", container)
            if port == 8080:
                logs = docker("logs", container)
                assert "Graceful shutdown complete" in logs
                assert not any(marker in logs for marker in markers)
            print(f"PASS: {image}: non-root, HTTP smoke, secret-marker log check and shutdown")
        print("Local checks only; web HTML is not authenticated browser interaction or AWS health evidence.")
    finally:
        for container in reversed(containers):
            docker("rm", "-f", container)


if __name__ == "__main__":
    main()
