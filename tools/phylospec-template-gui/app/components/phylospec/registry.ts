import type { PhyloSpecComponent, PhyloSpecGeneratorComponent } from './types'

const registry = new Map<string, PhyloSpecComponent<unknown>[]>()
const generatorRegistry = new Map<string, PhyloSpecGeneratorComponent<unknown>>()
const aliases = new Map<string, string>()

export function register<T>(component: PhyloSpecComponent<T>): void {
  const existing = registry.get(component.outputType) ?? []
  registry.set(component.outputType, [...existing, component as PhyloSpecComponent<unknown>])
}

/** Later registration replaces an earlier one for the same `generatorName`. */
export function registerGeneratorComponent<T>(
  component: PhyloSpecGeneratorComponent<T>,
): void {
  generatorRegistry.set(
    component.generatorName,
    component as PhyloSpecGeneratorComponent<unknown>,
  )
}

export function getGeneratorComponent(
  generatorName: string,
): PhyloSpecGeneratorComponent<unknown> | undefined {
  return generatorRegistry.get(generatorName)
}

export function registerAlias(alias: string, target: string): void {
  aliases.set(alias, target)
}

export function resolveAlias(type: string): string {
  const target = aliases.get(type)
  return target ? resolveAlias(target) : type
}

export function getComponents(type: string): PhyloSpecComponent<unknown>[] {
  const direct = registry.get(type) ?? []
  const aliasTarget = aliases.get(type)
  if (!aliasTarget) return direct
  const inherited = getComponents(aliasTarget)
  const seen = new Set(direct.map((c) => c.id))
  return [...direct, ...inherited.filter((c) => !seen.has(c.id))]
}
