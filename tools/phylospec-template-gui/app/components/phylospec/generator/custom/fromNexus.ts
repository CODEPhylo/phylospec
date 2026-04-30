import { z, ZodType } from "zod";
import coreComponents from "@/app/core-components.json";
import { registerGeneratorComponent } from "../../registry";
import type { GeneratorInputValue } from "../GeneratorInput";
import { buildExpression } from "../expression";
import { FromNexusGeneratorInput } from "./FromNexusGeneratorInput";

const def = coreComponents.componentLibrary.generators.find(
  (g) => g.name === "fromNexus",
);
if (!def) {
  throw new Error("fromNexus generator missing from core-components.json");
}

const genArgs = def.arguments as {
  name: string;
  type: string;
  description: string;
  required: boolean;
  default?: unknown;
}[];

registerGeneratorComponent({
  generatorName: "fromNexus",
  id: "generator.fromNexus",
  label: "fromNexus",
  schema: z.record(z.string(), z.any()) as ZodType<GeneratorInputValue>,
  Component: (props) =>
    FromNexusGeneratorInput({
      ...props,
      args: genArgs,
      description: def.description,
      collapseOptionalArgs: true,
    }),
  toExpression: (value: GeneratorInputValue) =>
    buildExpression("fromNexus", genArgs, value),
});
