import "./custom";
import { z, ZodType } from "zod";
import {
  register,
  registerAlias,
  getGeneratorComponent,
} from "../registry";
import { GeneratorInput, GeneratorInputValue } from "./GeneratorInput";
import { buildExpression } from "./expression";
import coreComponents from "@/app/core-components.json";

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

  const override = getGeneratorComponent(genName);
  if (override) {
    register({
      id: override.id,
      label: override.label,
      outputType: genType,
      isLiteral: false,
      schema: override.schema,
      Component: override.Component,
      toExpression: override.toExpression,
    });
    continue;
  }

  register({
    id: `generator.${genName}`,
    label: genName,
    outputType: genType,
    isLiteral: false,
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
