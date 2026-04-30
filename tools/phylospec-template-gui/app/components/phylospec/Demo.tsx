'use client'

import Link from 'next/link'
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
    <main className="min-h-screen bg-background text-foreground">
      <header className="w-full border-b border-gray-200 bg-light-gray/80 p-4 backdrop-blur-md">
        <div className="mx-auto flex w-full max-w-7xl items-center">
          <Link className="text-2xl font-bold flex-1" href="/">
            <span className="text-accent">Phylo</span><span className="text-gray-900">Spec</span>
          </Link>
        </div>
      </header>
      <div className="mx-auto w-full max-w-7xl px-6 py-8">
        <TemplateForm template={TEMPLATE} config={CONFIG} />
      </div>
    </main>
  )
}
