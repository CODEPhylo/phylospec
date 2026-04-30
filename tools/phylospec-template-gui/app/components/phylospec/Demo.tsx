'use client'

import { TemplateForm } from './TemplateForm'

const TEMPLATE = `Alignment data = fromNexus($fileName)
Tree tree ~ $tree
QMatrix qMatrix = $qMatrix

Alignment alignment ~ PhyloCTMC(
  tree, qMatrix
) observed as data`

const CONFIG = {
  '$fileName': { type: 'String',              description: 'Choose the nexus file with the alignment.' },
  '$tree':     { type: 'Distribution<Tree>',  description: 'Specify a prior distribution over the tree topology and branch lengths.' },
  '$qMatrix':  { type: 'QMatrix',             description: 'Select a substitution model for the alignment.' },
}

export function Demo() {
  return (
    <div className="p-8">
      <TemplateForm template={TEMPLATE} config={CONFIG} />
    </div>
  )
}
