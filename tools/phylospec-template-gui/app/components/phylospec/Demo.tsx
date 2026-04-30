'use client'

import { TemplateForm } from './TemplateForm'

const TEMPLATE = `Alignment data = fromNexus($fileName)
Tree tree ~ $tree
QMatrix qMatrix = $qMatrix

Alignment alignment ~ PhyloCTMC(
  tree, qMatrix
) observed as data`

const CONFIG = {
  '$fileName': { type: 'String' },
  '$tree':     { type: 'Tree' },
  '$qMatrix':  { type: 'QMatrix' },
}

export function Demo() {
  return (
    <div className="p-8">
      <TemplateForm template={TEMPLATE} config={CONFIG} />
    </div>
  )
}
