'use client'

import { GeneratorInput, GeneratorArg, GeneratorInputValue } from './GeneratorInput'
import { DensityPlot } from './DensityPlot'

type ScalarDistributionInputProps = {
  value: GeneratorInputValue | null
  onChange: (value: GeneratorInputValue | null) => void
  args: GeneratorArg[]
  description?: string
  densityFn: (params: Record<string, number>, x: number) => number
  xRange: (params: Record<string, number>) => [number, number] | null
}

export function ScalarDistributionInput({
  value,
  onChange,
  args,
  description,
  densityFn,
  xRange,
}: ScalarDistributionInputProps) {
  const params: Record<string, number> = {}
  for (const arg of args) {
    const entry = value?.[arg.name]
    if (entry && !entry.isDistribution && typeof entry.value === 'number' && Number.isFinite(entry.value)) {
      params[arg.name] = entry.value
    }
  }

  const requiredArgs = args.filter((a) => a.required)
  const paramsComplete = requiredArgs.every((a) => a.name in params)
  const computedRange = paramsComplete ? xRange(params) : null

  return (
    <div className="flex flex-col gap-4">
      {description && (
        <p className="text-sm text-gray-600 dark:text-gray-400 italic">{description}</p>
      )}
      <div className="flex gap-4 items-start">
        <div className="flex-1 min-w-0">
          <GeneratorInput
            value={value}
            onChange={onChange}
            args={args}
          />
        </div>
        <div className="w-1/3 shrink-0 h-40 bg-gradient-to-br from-gray-50 to-white border border-gray-200/70 rounded-xl overflow-hidden flex flex-col shadow-sm">
          <div className="px-3 py-1.5 border-b border-gray-200/70 text-xs font-semibold uppercase tracking-wider text-gray-400">
            Preview
          </div>
          <div className="flex-1 min-h-0 px-1 pt-1 relative">
            {computedRange ? (
              <DensityPlot
                densityFn={(x) => densityFn(params, x)}
                xRange={computedRange}
              />
            ) : (
              <div className="absolute inset-0 flex items-center justify-center">
                <span className="text-xs text-gray-300 select-none">fill in parameters</span>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
