import { z, ZodType } from "zod";
import { register, registerAlias, getComponents } from "../registry";
import { GeneratorInput, GeneratorInputValue } from "./GeneratorInput";
import coreComponents from "@/app/core-components.json";

export const DEFAULT_COMPONENT_ID = "__default__";

function indentContinuation(expr: string, col: number): string {
  if (!expr.includes("\n")) return expr;
  const pad = " ".repeat(col);
  return expr
    .split("\n")
    .map((line, i) => (i === 0 ? line : pad + line))
    .join("\n");
}

function buildExpression(
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
    const component = getComponents(arg.type).find(
      (c) => c.id === argValue.componentId,
    );
    if (!component) continue;
    const prefix = `    ${arg.name}=`;
    const expr = component.toExpression(argValue.value);
    lines.push(prefix + indentContinuation(expr, prefix.length));
  }

  if (lines.length === 0) return `${name}()`;
  return `${name}(\n${lines.join(",\n")}\n)`;
}

for (const type of coreComponents.componentLibrary.types) {
  if ("alias" in type && typeof type.alias === "string") {
    registerAlias(type.name, type.alias);
  }
}

const seen = new Set<string>();

for (const generator of coreComponents.componentLibrary.generators) {
  if (seen.has(generator.name)) continue;
  seen.add(generator.name);

  const genName = generator.name;
  const genType = generator.generatedType;
  const genArgs = generator.arguments as {
    name: string;
    type: string;
    description: string;
    required: boolean;
    default?: unknown;
  }[];

  register({
    id: `generator.${genName}`,
    label: genName,
    outputType: genType,
    schema: z.record(z.string(), z.any()) as ZodType<GeneratorInputValue>,
    Component: (props) =>
      GeneratorInput({
        ...props,
        args: genArgs,
        description: generator.description,
      }),
    toExpression: (value: GeneratorInputValue) =>
      buildExpression(genName, genArgs, value),
  });
}
