import type { PhyloSpecComponent } from './types'

const registry = new Map<string, PhyloSpecComponent<unknown>[]>()

export function register<T>(component: PhyloSpecComponent<T>): void {
  const existing = registry.get(component.outputType) ?? []
  registry.set(component.outputType, [...existing, component as PhyloSpecComponent<unknown>])
}

export function getComponents(type: string): PhyloSpecComponent<unknown>[] {
  return registry.get(type) ?? []
}
