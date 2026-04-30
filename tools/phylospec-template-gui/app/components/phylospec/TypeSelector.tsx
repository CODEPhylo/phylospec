'use client'

import { useState } from 'react'
import { getComponents } from './registry'

export type TypeSelectorValue = {
  componentId: string
  value: unknown
}

type TypeSelectorProps = {
  type: string
  value: TypeSelectorValue | null
  onChange: (v: TypeSelectorValue) => void
}

export function TypeSelector({ type, value, onChange }: TypeSelectorProps) {
  const components = getComponents(type)

  const activeId = value?.componentId ?? components[0]?.id ?? null
  const [selectedId, setSelectedId] = useState<string | null>(activeId)

  if (components.length === 0) {
    return <span className="text-xs text-gray-400">No components registered for type &quot;{type}&quot;</span>
  }

  const effectiveId = selectedId ?? components[0].id
  const registration = components.find((c) => c.id === effectiveId) ?? components[0]
  const currentValue = value?.componentId === effectiveId ? value.value : null

  function handleSelect(id: string) {
    setSelectedId(id)
    onChange({ componentId: id, value: null })
  }

  function handleChange(v: unknown) {
    onChange({ componentId: effectiveId, value: v })
  }

  return (
    <div className="flex flex-col gap-2">
      {components.length > 1 && (
        <div className="flex flex-wrap gap-1">
          {components.map((c) => (
            <button
              key={c.id}
              onClick={() => handleSelect(c.id)}
              className={`rounded px-2 py-0.5 text-xs ${
                c.id === effectiveId
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-100 text-gray-700 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600'
              }`}
            >
              {c.label}
            </button>
          ))}
        </div>
      )}
      <registration.Component
        value={currentValue}
        onChange={handleChange}
      />
    </div>
  )
}
