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
  const direct = aliases.get(type)
  if (direct) return resolveAlias(direct)
  // resolve parameterised types like Distribution<Rate> → Distribution<PositiveReal>
  const m = type.match(/^([^<]+)<(.+)>$/)
  if (m) {
    const inner = resolveAlias(m[2])
    if (inner !== m[2]) return `${m[1]}<${inner}>`
  }
  return type
}

export function getComponents(type: string): PhyloSpecComponent<unknown>[] {
  const direct = registry.get(type) ?? []
  const aliasTarget = aliases.get(type)
  if (aliasTarget) {
    const inherited = getComponents(aliasTarget)
    const seen = new Set(direct.map((c) => c.id))
    return [...direct, ...inherited.filter((c) => !seen.has(c.id))]
  }
  // fall back to resolving parameterised aliases (e.g. Distribution<Rate> → Distribution<PositiveReal>)
  const resolved = resolveAlias(type)
  if (resolved !== type) return getComponents(resolved)
  return direct
}
