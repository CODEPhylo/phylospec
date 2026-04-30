import { z } from 'zod'
import { register } from '../registry'
import { IntegerInput } from './IntegerInput'
import { RealInput } from './RealInput'
import { StringInput } from './StringInput'

// integer types

const integerSchema = z.number().int()
register({
  id: 'literal.integer',
  label: 'Specific Value',
  outputType: 'Integer',
  schema: integerSchema,
  Component: (props) => IntegerInput({ ...props, schema: integerSchema }),
  toExpression: String,
})

const positiveIntegerSchema = z.number().int().positive()
register({
  id: 'literal.positiveInteger',
  label: 'Specific Value',
  outputType: 'PositiveInteger',
  schema: positiveIntegerSchema,
  Component: (props) => IntegerInput({ ...props, schema: positiveIntegerSchema, min: 1 }),
  toExpression: String,
})

const nonNegativeIntegerSchema = z.number().int().nonnegative()
register({
  id: 'literal.nonNegativeInteger',
  label: 'Specific Value',
  outputType: 'NonNegativeInteger',
  schema: nonNegativeIntegerSchema,
  Component: (props) => IntegerInput({ ...props, schema: nonNegativeIntegerSchema, min: 0 }),
  toExpression: String,
})

// real types

const realSchema = z.number()
register({
  id: 'literal.real',
  label: 'Specific Value',
  outputType: 'Real',
  schema: realSchema,
  Component: (props) => RealInput({ ...props, schema: realSchema }),
  toExpression: String,
})

const positiveRealSchema = z.number().positive()
register({
  id: 'literal.positiveReal',
  label: 'Specific Value',
  outputType: 'PositiveReal',
  schema: positiveRealSchema,
  Component: (props) => RealInput({ ...props, schema: positiveRealSchema, min: 0 }),
  toExpression: String,
})

const nonNegativeRealSchema = z.number().nonnegative()
register({
  id: 'literal.nonNegativeReal',
  label: 'Specific Value',
  outputType: 'NonNegativeReal',
  schema: nonNegativeRealSchema,
  Component: (props) => RealInput({ ...props, schema: nonNegativeRealSchema, min: 0 }),
  toExpression: String,
})

const probabilitySchema = z.number().min(0).max(1)
register({
  id: 'literal.probability',
  label: 'Specific Value',
  outputType: 'Probability',
  schema: probabilitySchema,
  Component: (props) => RealInput({ ...props, schema: probabilitySchema, min: 0, max: 1 }),
  toExpression: String,
})

// string

const stringSchema = z.string()
register({
  id: 'literal.string',
  label: 'Specific Value',
  outputType: 'String',
  schema: stringSchema,
  Component: (props) => StringInput({ ...props, schema: stringSchema }),
  toExpression: (v) => typeof v === 'string' ? `"${v}"` : '',
})
