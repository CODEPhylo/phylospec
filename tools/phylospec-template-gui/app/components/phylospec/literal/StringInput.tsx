'use client'

import { fromZodError } from 'zod-validation-error'
import type { ZodType } from 'zod'
import type { ComponentProps } from '../types'

type StringInputProps = ComponentProps<string> & {
  schema: ZodType<string>
}

export function StringInput({ value, onChange, schema }: StringInputProps) {
  const raw = value ?? ''
  const result = raw !== '' ? schema.safeParse(raw) : null
  const isValid = result?.success === true
  const error = result && !result.success ? fromZodError(result.error).message : null

  return (
    <div className="flex flex-col gap-1">
      <input
        type="text"
        value={raw}
        onChange={(e) => onChange(e.target.value || null)}
        className={`w-48 rounded-lg border px-2.5 py-1.5 text-sm bg-white outline-none transition-all focus:ring-2 focus:ring-accent/20 dark:bg-gray-800 ${
          isValid
            ? 'border-green-500 dark:border-green-400'
            : error
            ? 'border-red-500 dark:border-red-400'
            : 'border-gray-300 dark:border-gray-600'
        }`}
      />
      {error && <span className="text-sm text-red-500">{error}</span>}
    </div>
  )
}
