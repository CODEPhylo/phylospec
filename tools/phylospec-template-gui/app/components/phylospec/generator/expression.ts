import { getComponents, resolveAlias } from "../registry";
import { DEFAULT_COMPONENT_ID } from "./constants";
import type { GeneratorInputValue } from "./GeneratorInput";

function indentContinuation(expr: string, col: number): string {
  if (!expr.includes("\n")) return expr;
  const pad = " ".repeat(col);
  return expr
    .split("\n")
    .map((line, i) => (i === 0 ? line : pad + line))
    .join("\n");
}

export function buildExpression(
  name: string,
  args: { name: string; type: string }[],
  value: GeneratorInputValue,
): string {
  const lines: string[] = [];

  for (const arg of args) {
    const argValue = value[arg.name];
    if (!argValue || argValue.value === null) continue;
    if (argValue.componentId === DEFAULT_COMPONENT_ID) {
      const prefix = `    ${arg.name}=`;
      lines.push(
        prefix + indentContinuation(argValue.value as string, prefix.length),
      );
      continue;
    }
    const searchTypes = argValue.isDistribution
      ? [arg.type, `Distribution<${resolveAlias(arg.type)}>`]
      : [arg.type];
    const allCandidates = searchTypes.flatMap((t) => getComponents(t));
    const component = allCandidates.find((c) => c.id === argValue.componentId);
    if (!component) continue;
    const prefix = `    ${arg.name}=`;
    const expr = component.toExpression(argValue.value);
    lines.push(prefix + indentContinuation(expr, prefix.length));
  }

  if (lines.length === 0) return `${name}()`;
  return `${name}(\n${lines.join(",\n")}\n)`;
}
