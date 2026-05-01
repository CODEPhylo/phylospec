import { z, ZodType } from "zod";
import coreComponents from "@/app/core-components.json";
import { registerGeneratorComponent } from "../../registry";
import type { GeneratorInputValue, GeneratorArg } from "../GeneratorInput";
import { buildExpression } from "../expression";
import { ScalarDistributionInput } from "../ScalarDistributionInput";

// Lanczos log-gamma approximation, accurate for x > 0
function logGamma(x: number): number {
  const g = 7;
  const c = [
    0.99999999999980993, 676.5203681218851, -1259.1392167224028,
    771.32342877765313, -176.61502916214059, 12.507343278686905,
    -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7,
  ];
  if (x < 0.5)
    return Math.log(Math.PI / Math.sin(Math.PI * x)) - logGamma(1 - x);
  x -= 1;
  let a = c[0];
  const t = x + g + 0.5;
  for (let i = 1; i < g + 2; i++) a += c[i] / (x + i);
  return (
    0.5 * Math.log(2 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(a)
  );
}

function normalPdf(x: number, mean: number, sd: number): number {
  return (
    (1 / (sd * Math.sqrt(2 * Math.PI))) *
    Math.exp(-0.5 * ((x - mean) / sd) ** 2)
  );
}

function logNormalPdf(x: number, logMean: number, logSd: number): number {
  if (x <= 0) return 0;
  return (
    (1 / (x * logSd * Math.sqrt(2 * Math.PI))) *
    Math.exp(-0.5 * ((Math.log(x) - logMean) / logSd) ** 2)
  );
}

function gammaPdf(x: number, shape: number, rate: number): number {
  if (x <= 0) return 0;
  return Math.exp(
    shape * Math.log(rate) -
      logGamma(shape) +
      (shape - 1) * Math.log(x) -
      rate * x,
  );
}

function betaPdf(x: number, alpha: number, beta: number): number {
  if (x <= 0 || x >= 1) return 0;
  const logB = logGamma(alpha) + logGamma(beta) - logGamma(alpha + beta);
  return Math.exp(
    (alpha - 1) * Math.log(x) + (beta - 1) * Math.log(1 - x) - logB,
  );
}

function exponentialPdf(x: number, rate: number): number {
  if (x < 0) return 0;
  return rate * Math.exp(-rate * x);
}

function getDef(name: string) {
  const def = coreComponents.componentLibrary.generators.find(
    (g) => g.name === name,
  );
  if (!def)
    throw new Error(`generator ${name} missing from core-components.json`);
  return def;
}

function makeSchema() {
  return z.record(z.string(), z.any()) as ZodType<GeneratorInputValue>;
}

// Normal
{
  const def = getDef("Normal");
  const genArgs = def.arguments as GeneratorArg[];
  registerGeneratorComponent({
    generatorName: "Normal",
    id: "generator.Normal",
    label: "Normal",
    schema: makeSchema(),
    Component: (props) =>
      ScalarDistributionInput({
        ...props,
        args: genArgs,
        description: def.description,
        densityFn: (p, x) => normalPdf(x, p.mean, p.sd),
        xRange: (p) =>
          p.sd > 0 ? [p.mean - 4 * p.sd, p.mean + 4 * p.sd] : null,
      }),
    toExpression: (v: GeneratorInputValue) =>
      buildExpression("Normal", genArgs, v),
  });
}

// LogNormal
{
  const def = getDef("LogNormal");
  const genArgs = def.arguments as GeneratorArg[];
  registerGeneratorComponent({
    generatorName: "LogNormal",
    id: "generator.LogNormal",
    label: "LogNormal",
    schema: makeSchema(),
    Component: (props) =>
      ScalarDistributionInput({
        ...props,
        args: genArgs,
        description: def.description,
        densityFn: (p, x) => logNormalPdf(x, p.logMean, p.logSd),
        xRange: (p) =>
          p.logSd > 0 ? [1e-8, Math.exp(p.logMean + 2 * p.logSd)] : null,
      }),
    toExpression: (v: GeneratorInputValue) =>
      buildExpression("LogNormal", genArgs, v),
  });
}

// Gamma
{
  const def = getDef("Gamma");
  const genArgs = def.arguments as GeneratorArg[];
  registerGeneratorComponent({
    generatorName: "Gamma",
    id: "generator.Gamma",
    label: "Gamma",
    schema: makeSchema(),
    Component: (props) =>
      ScalarDistributionInput({
        ...props,
        args: genArgs,
        description: def.description,
        densityFn: (p, x) => gammaPdf(x, p.shape, p.rate),
        xRange: (p) =>
          p.shape > 0 && p.rate > 0
            ? [1e-8, (p.shape + 4 * Math.sqrt(p.shape)) / p.rate]
            : null,
      }),
    toExpression: (v: GeneratorInputValue) =>
      buildExpression("Gamma", genArgs, v),
  });
}

// Beta
{
  const def = getDef("Beta");
  const genArgs = def.arguments as GeneratorArg[];
  registerGeneratorComponent({
    generatorName: "Beta",
    id: "generator.Beta",
    label: "Beta",
    schema: makeSchema(),
    Component: (props) =>
      ScalarDistributionInput({
        ...props,
        args: genArgs,
        description: def.description,
        densityFn: (p, x) => betaPdf(x, p.alpha, p.beta),
        xRange: (p) => (p.alpha > 0 && p.beta > 0 ? [0.001, 0.999] : null),
      }),
    toExpression: (v: GeneratorInputValue) =>
      buildExpression("Beta", genArgs, v),
  });
}

// Exponential
{
  const def = getDef("Exponential");
  const genArgs = def.arguments as GeneratorArg[];
  registerGeneratorComponent({
    generatorName: "Exponential",
    id: "generator.Exponential",
    label: "Exponential",
    schema: makeSchema(),
    Component: (props) =>
      ScalarDistributionInput({
        ...props,
        args: genArgs,
        description: def.description,
        densityFn: (p, x) => exponentialPdf(x, p.rate),
        xRange: (p) => (p.rate > 0 ? [0, 5 / p.rate] : null),
      }),
    toExpression: (v: GeneratorInputValue) =>
      buildExpression("Exponential", genArgs, v),
  });
}
