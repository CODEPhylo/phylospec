'use client'

// triggers register() side-effects in literal/index.ts via the client bundle
import '@/app/components/phylospec'

import { useState } from 'react'
import { TypeSelector, type TypeSelectorValue } from './TypeSelector'
import { getComponents } from './registry'

const DEMO_TYPES = [
  'Integer',
  'PositiveInteger',
  'NonNegativeInteger',
  'Real',
  'PositiveReal',
  'Probability',
  'Rate',
  'Age',
  'String',
] as const

export function Demo() {
  const [values, setValues] = useState<Record<string, TypeSelectorValue | null>>(
    Object.fromEntries(DEMO_TYPES.map((t) => [t, null]))
  )

  function expressionFor(type: string): string | null {
    const v = values[type]
    if (!v || v.value === null) return null
    const reg = getComponents(type).find((c) => c.id === v.componentId)
    return reg ? reg.toExpression(v.value) : null
  }

  return (
    <div className="flex flex-col gap-6 p-8 font-mono text-sm">
      <h1 className="text-xl font-semibold font-sans">PhyloSpec Component Registry — Demo</h1>
      <div className="grid grid-cols-[auto_1fr_auto] items-start gap-x-6 gap-y-4">
        {DEMO_TYPES.map((type) => (
          <>
            <span key={`label-${type}`} className="pt-1 font-medium text-gray-700 dark:text-gray-300">
              {type}
            </span>
            <TypeSelector
              key={`selector-${type}`}
              type={type}
              value={values[type]}
              onChange={(v) => setValues((prev) => ({ ...prev, [type]: v }))}
            />
            <span key={`expr-${type}`} className="pt-1 text-blue-600 dark:text-blue-400">
              {expressionFor(type) ?? '—'}
            </span>
          </>
        ))}
      </div>
    </div>
  )
}
