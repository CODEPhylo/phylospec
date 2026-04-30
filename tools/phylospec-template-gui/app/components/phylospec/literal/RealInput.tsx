'use client'

import { useState, useEffect } from 'react'
import { fromZodError } from 'zod-validation-error'
import type { ZodType } from 'zod'
import type { ComponentProps } from '../types'

type RealInputProps = ComponentProps<number> & {
  schema: ZodType<number>
  min?: number
  max?: number
}

export function RealInput({ value, onChange, schema, min, max }: RealInputProps) {
  const [raw, setRaw] = useState(value != null ? String(value) : '')

  // sync display when parent resets the value (e.g. component switch)
  useEffect(() => {
    if (value === null) setRaw('')
  }, [value])

  const parsed = parseFloat(raw)
  // incomplete inputs that shouldn't trigger errors yet
  const isEmptyOrIncomplete = raw === '' || raw === '-' || raw === '.' || raw === '-.'
  const validationResult = !isEmptyOrIncomplete && !isNaN(parsed) ? schema.safeParse(parsed) : null
  const isValid = validationResult?.success === true
  const error = validationResult && !validationResult.success
    ? fromZodError(validationResult.error).message
    : null

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const str = e.target.value
    setRaw(str)
    const p = parseFloat(str)
    if (!isNaN(p)) {
      const result = schema.safeParse(p)
      onChange(result.success ? p : null)
    } else {
      onChange(null)
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <input
        type="number"
        step="any"
        min={min}
        max={max}
        value={raw}
        onChange={handleChange}
        className={`w-32 rounded border px-2 py-1 text-sm bg-white dark:bg-gray-800 ${
          isValid
            ? 'border-green-500 dark:border-green-400'
            : error
            ? 'border-red-500 dark:border-red-400'
            : 'border-gray-300 dark:border-gray-600'
        }`}
      />
      {error && <span className="text-xs text-red-500">{error}</span>}
    </div>
  )
}
