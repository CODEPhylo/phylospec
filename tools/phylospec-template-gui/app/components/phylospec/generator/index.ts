import { z, ZodType } from 'zod'
import { register, getComponents } from '../registry'
import { GeneratorInput, GeneratorInputValue } from './GeneratorInput'
import coreComponents from '@/app/core-components.json'

function buildExpression(
  name: string,
  args: { name: string; type: string }[],
  value: GeneratorInputValue
): string {
  const lines: string[] = []

  for (const arg of args) {
    const argValue = value[arg.name]
    if (!argValue || argValue.value === null) continue
    const component = getComponents(arg.type).find((c) => c.id === argValue.componentId)
    if (!component) continue
    lines.push(`    ${arg.name}=${component.toExpression(argValue.value)}`)
  }

  if (lines.length === 0) return `${name}()`
  return `${name}(\n${lines.join(',\n')}\n)`
}

const seen = new Set<string>()

for (const generator of coreComponents.componentLibrary.generators) {
  if (seen.has(generator.name)) continue
  seen.add(generator.name)

  const genName = generator.name
  const genType = generator.generatedType
  const genArgs = generator.arguments as { name: string; type: string; description: string; required: boolean; default?: unknown }[]

  register({
    id: `generator.${genName}`,
    label: genName,
    outputType: genType,
    schema: z.record(z.string(), z.any()) as ZodType<GeneratorInputValue>,
    Component: (props) => GeneratorInput({ ...props, args: genArgs }),
    toExpression: (value: GeneratorInputValue) => buildExpression(genName, genArgs, value),
  })
}
