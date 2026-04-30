'use client'

import { TemplateForm } from './TemplateForm'

const TEMPLATE = `Alignment data = fromNexus($fileName)
Tree tree ~ $tree
QMatrix qMatrix = $qMatrix

Alignment alignment ~ PhyloCTMC(
  tree, qMatrix
) observed as data`

const CONFIG = {
  experimentName: 'PhyloCTMC Analysis',
  defaults: {
    taxa: 'taxa(data)',
    alignment: 'data',
    tree: 'tree',
  },
  placeholders: {
    '$fileName': { type: 'String',             name: 'Nexus File',         description: 'Choose the nexus file with the alignment.' },
    '$tree':     { type: 'Distribution<Tree>', name: 'Tree Prior',         description: 'Specify a prior distribution over the tree topology and branch lengths.' },
    '$qMatrix':  { type: 'QMatrix',            name: 'Substitution Model', description: 'Select a substitution model for the alignment.' },
  },
}

export function Demo() {
  return (
    <div className="p-8">
      <TemplateForm template={TEMPLATE} config={CONFIG} />
    </div>
  )
}
