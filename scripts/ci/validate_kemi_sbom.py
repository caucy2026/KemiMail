#!/usr/bin/env python3
"""Validate KEMI release SBOM policy without printing SBOM contents."""

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path


MAX_SBOM_BYTES = 16 * 1024 * 1024
SENSITIVE_KEYS = {
    "apikey",
    "credential",
    "keypass",
    "password",
    "passwd",
    "privatekey",
    "secret",
    "storepass",
    "token",
}
WINDOWS_ABSOLUTE_PATH = re.compile(r"^(?:[A-Za-z]:[\\/]|\\\\)")
UNIX_LOCAL_PATH = re.compile(r"^/(?:home|Users|private|tmp|var/tmp|workspace|workspaces)(?:/|$)")
CREDENTIAL_URL = re.compile(r"^[a-z][a-z0-9+.-]*://[^/\s:@]+:[^/\s@]+@", re.IGNORECASE)


class SbomValidationError(ValueError):
    """Raised when an SBOM violates the KEMI release policy."""


@dataclass(frozen=True)
class SbomSummary:
    component_count: int
    dependency_edge_count: int


def _require(condition, message):
    if not condition:
        raise SbomValidationError(message)


def _walk(value, json_path="$"):
    yield json_path, value
    if isinstance(value, dict):
        for key, child in value.items():
            yield from _walk(child, f"{json_path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from _walk(child, f"{json_path}[{index}]")


def _validate_privacy(document):
    for json_path, value in _walk(document):
        if isinstance(value, dict):
            for key in value:
                normalized_key = re.sub(r"[^a-z]", "", key.lower())
                if normalized_key in SENSITIVE_KEYS or any(
                    normalized_key.endswith(suffix) for suffix in ("password", "secret", "token")
                ):
                    raise SbomValidationError(f"发现禁止的敏感字段：{json_path}.{key}")
        elif isinstance(value, str):
            if (
                WINDOWS_ABSOLUTE_PATH.search(value)
                or UNIX_LOCAL_PATH.search(value)
                or value.lower().startswith("file://")
            ):
                raise SbomValidationError(f"发现本机绝对路径：{json_path}")
            if CREDENTIAL_URL.search(value) or "-----BEGIN PRIVATE KEY-----" in value:
                raise SbomValidationError(f"发现凭据或私钥内容：{json_path}")


def _collect_components(components):
    collected = []
    for component in components:
        _require(isinstance(component, dict), "components 必须只包含对象")
        collected.append(component)
        nested = component.get("components", [])
        _require(isinstance(nested, list), "嵌套 components 必须是数组")
        collected.extend(_collect_components(nested))
    return collected


def validate_sbom(path, version):
    path = Path(path)
    _require(path.is_file(), f"SBOM 文件不存在：{path}")
    _require(path.stat().st_size <= MAX_SBOM_BYTES, "SBOM 超过 16 MiB 上限")

    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise SbomValidationError("SBOM 不是有效的 UTF-8 JSON") from error

    _require(isinstance(document, dict), "SBOM 顶层必须是对象")
    _require(document.get("bomFormat") == "CycloneDX", "bomFormat 必须是 CycloneDX")
    _require(document.get("specVersion") == "1.7", "specVersion 必须是 1.7")
    _require(isinstance(document.get("version"), int) and document["version"] >= 1, "version 必须是正整数")
    _require("serialNumber" not in document, "为保持可复核输出，SBOM 不得包含随机 serialNumber")

    metadata = document.get("metadata")
    _require(isinstance(metadata, dict), "metadata 必须存在且为对象")
    root_component = metadata.get("component")
    _require(isinstance(root_component, dict), "metadata.component 必须存在且为对象")
    _require(root_component.get("type") == "application", "根组件类型必须是 application")
    _require(root_component.get("name") == "KEMI Mail", "根组件名称必须是 KEMI Mail")
    _require(root_component.get("version") == version, "根组件版本与发行版本不匹配")
    root_reference = root_component.get("bom-ref")
    _require(isinstance(root_reference, str) and root_reference, "根组件必须包含非空 bom-ref")

    raw_components = document.get("components")
    _require(isinstance(raw_components, list) and raw_components, "components 必须是非空数组")
    components = _collect_components(raw_components)

    references = {root_reference}
    for component in components:
        _require(isinstance(component.get("type"), str) and component["type"], "每个组件必须包含 type")
        _require(isinstance(component.get("name"), str) and component["name"], "每个组件必须包含 name")
        _require(isinstance(component.get("version"), str) and component["version"], "每个组件必须包含 version")
        reference = component.get("bom-ref")
        _require(isinstance(reference, str) and reference, "每个组件必须包含非空 bom-ref")
        _require(reference not in references, f"组件 bom-ref 重复：{reference}")
        references.add(reference)

    dependencies = document.get("dependencies")
    _require(isinstance(dependencies, list) and dependencies, "dependencies 必须是非空数组")
    dependency_references = set()
    dependency_edge_count = 0
    for dependency in dependencies:
        _require(isinstance(dependency, dict), "dependencies 必须只包含对象")
        reference = dependency.get("ref")
        _require(reference in references, f"依赖图引用未知组件：{reference}")
        _require(reference not in dependency_references, f"依赖图 ref 重复：{reference}")
        dependency_references.add(reference)
        depends_on = dependency.get("dependsOn", [])
        _require(isinstance(depends_on, list), "dependsOn 必须是数组")
        for target in depends_on:
            _require(target in references, f"依赖图引用未知组件：{target}")
        dependency_edge_count += len(depends_on)

    _require(root_reference in dependency_references, "依赖图必须包含根组件")
    _require(dependency_edge_count > 0, "依赖图至少需要一条边，不能退化为直接依赖文本清单")
    _validate_privacy(document)

    return SbomSummary(component_count=len(components), dependency_edge_count=dependency_edge_count)


def main():
    parser = argparse.ArgumentParser(description="校验 KEMI 发布 SBOM 的结构、图完整性和隐私边界")
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--version", required=True)
    arguments = parser.parse_args()

    try:
        summary = validate_sbom(arguments.input, arguments.version)
    except SbomValidationError as error:
        print(f"KEMI SBOM 校验失败：{error}", file=sys.stderr)
        return 1

    print(
        "KEMI SBOM 校验通过："
        f"components={summary.component_count}, dependencyEdges={summary.dependency_edge_count}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
