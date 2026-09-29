#!/usr/bin/env python3
"""Runs the API checks from DATA-API.yaml against a running JOIN.

Usage:
    JOIN_USER_A_EMAIL=... JOIN_USER_A_PASSWORD=... \\
    JOIN_USER_B_EMAIL=... JOIN_USER_B_PASSWORD=... \\
    python3 scripts/check-api.py [--base-url https://localhost] [--insecure] [DATA-API.yaml]

Needs PyYAML (scripts/requirements.txt). Exit code 1 if a required check fails.
"""
import argparse
import json
import os
import re
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request

import yaml

_MISSING = object()


def interpolate(value, variables):
    """${ENV} from the environment, {name} from test data and saved values; non-strings as is."""
    if isinstance(value, dict):
        return {interpolate(k, variables): interpolate(v, variables) for k, v in value.items()}
    if isinstance(value, list):
        return [interpolate(v, variables) for v in value]
    if not isinstance(value, str):
        return value

    def env(match):
        name = match.group(1)
        if name not in os.environ:
            raise KeyError(f"переменная окружения {name} не задана")
        return os.environ[name]

    def var(match):
        name = match.group(1)
        if name not in variables:
            raise KeyError(f"нет значения {{{name}}} — предыдущая проверка не сохранила его")
        return str(variables[name])

    value = re.sub(r"\$\{(\w+)\}", env, value)
    return re.sub(r"\{(\w+)\}", var, value)


def _tokens(path):
    for key, index in re.findall(r"([^.\[\]]+)|\[([^\]]*)\]", path):
        yield ("key", key) if key else ("index", index)


def _matches(item, condition):
    for part in condition.split(","):
        field, _, expected = part.partition("=")
        if not isinstance(item, dict) or str(_scalar(item.get(field.strip()))) != expected.strip():
            return False
    return True


def _scalar(value):
    return str(value).lower() if isinstance(value, bool) else value


def resolve(data, path):
    """a.b[0].c, list[field=value].x (first match), list[*].x (all values)."""
    current = data
    for kind, token in _tokens(path):
        if kind == "key":
            if isinstance(current, list):  # after [*]
                current = [c.get(token, _MISSING) if isinstance(c, dict) else _MISSING for c in current]
            elif isinstance(current, dict) and token in current:
                current = current[token]
            else:
                return _MISSING
        elif token == "*":
            if not isinstance(current, list):
                return _MISSING
        elif token.lstrip("-").isdigit():
            if not isinstance(current, list) or not -len(current) <= int(token) < len(current):
                return _MISSING
            current = current[int(token)]
        else:
            if not isinstance(current, list):
                return _MISSING
            current = next((item for item in current if _matches(item, token)), _MISSING)
        if current is _MISSING:
            return _MISSING
    return current


def same(actual, expected):
    return str(_scalar(actual)) == str(_scalar(expected))


def run_check(check, roles, variables, base_url, context):
    request = interpolate(check["request"], variables)
    role = roles.get(check.get("role", "anonymous"), {})
    headers = {"Accept": "application/json"}
    headers.update(interpolate(role.get("headers", {}), variables))

    url = base_url.rstrip("/") + request["path"]
    if request.get("query"):
        url += "?" + urllib.parse.urlencode({k: str(_scalar(v)) for k, v in request["query"].items()})
    body = None
    if "body" in request:
        body = json.dumps(request["body"]).encode()
        headers["Content-Type"] = "application/json"

    req = urllib.request.Request(url, data=body, headers=headers, method=request["method"])
    try:
        with urllib.request.urlopen(req, timeout=30, context=context) as response:
            status, content_type, raw = response.status, response.headers.get("Content-Type", ""), response.read()
    except urllib.error.HTTPError as error:
        status, content_type, raw = error.code, error.headers.get("Content-Type", ""), error.read()

    expect = interpolate(check.get("expect", {}), variables)
    problems = []
    if status not in expect.get("status", [200]):
        problems.append(f"статус {status}, ожидался {expect.get('status')}")
    if expect.get("content_type") and not content_type.startswith(expect["content_type"]):
        problems.append(f"Content-Type {content_type or '—'}, ожидался {expect['content_type']}")

    data = None
    if raw:
        try:
            data = json.loads(raw)
        except ValueError:
            data = None
    for path in expect.get("required_fields", []):
        if resolve(data, path) in (_MISSING, None):
            problems.append(f"нет поля {path}")
    for path, expected in expect.get("equals", {}).items():
        actual = resolve(data, path)
        if actual is _MISSING or not same(actual, expected):
            problems.append(f"{path} = {actual if actual is not _MISSING else '—'}, ожидалось {expected}")
    for path, expected in expect.get("all", {}).items():
        values = resolve(data, path)
        if not isinstance(values, list) or not all(same(v, expected) for v in values):
            problems.append(f"не у всех {path} = {expected}")
    for path, expected in expect.get("contains", {}).items():
        values = resolve(data, path)
        if not isinstance(values, list) or not any(same(v, expected) for v in values):
            problems.append(f"среди {path} нет {expected}")

    if not problems:
        for name, path in check.get("save", {}).items():
            value = resolve(data, interpolate(path, variables))
            if value in (_MISSING, None):
                problems.append(f"не удалось сохранить {name} из {path}")
            else:
                variables[name] = value
    return status, problems


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("spec", nargs="?", default=os.path.join(os.path.dirname(__file__), "..", "DATA-API.yaml"))
    parser.add_argument("--base-url", help="по умолчанию — base_url из файла")
    parser.add_argument("--insecure", action="store_true", help="не проверять TLS (самоподписанный сертификат)")
    args = parser.parse_args()

    with open(args.spec, encoding="utf-8") as f:
        spec = yaml.safe_load(f)
    base_url = args.base_url or spec["base_url"]
    context = ssl._create_unverified_context() if args.insecure else None
    variables = dict(spec.get("test_data", {}))
    roles = spec.get("roles", {})

    print(f"{spec['solution']} — {base_url}")
    failed_required = 0
    for check in spec["checks"]:
        try:
            status, problems = run_check(check, roles, variables, base_url, context)
        except (KeyError, urllib.error.URLError, OSError) as error:
            status, problems = "—", [str(error)]
        mark = "OK  " if not problems else ("FAIL" if check.get("required", True) else "WARN")
        print(f"{mark} {check['id']:<26} {status!s:>4}  {check['title']}")
        for problem in problems:
            print(f"       {problem}")
        if problems and check.get("required", True):
            failed_required += 1

    total = sum(1 for c in spec["checks"] if c.get("required", True))
    print(f"\nОбязательных проверок пройдено: {total - failed_required} из {total}")
    sys.exit(1 if failed_required else 0)


if __name__ == "__main__":
    main()
