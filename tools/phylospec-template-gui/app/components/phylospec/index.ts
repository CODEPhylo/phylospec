import './literal'
import './generator'

export {
  register,
  getComponents,
  registerGeneratorComponent,
  getGeneratorComponent,
} from './registry'
export { TypeSelector } from './TypeSelector'
export type { TypeSelectorValue } from './TypeSelector'
export type {
  PhyloSpecComponent,
  PhyloSpecGeneratorComponent,
  ComponentProps,
} from './types'
export { TemplateForm } from './TemplateForm'
export type { TemplateFormProps } from './TemplateForm'
