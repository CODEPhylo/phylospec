'use client'

import Link from 'next/link'
import { TemplateForm } from './TemplateForm'

const TEMPLATE = `Alignment data = $alignment
Tree tree ~ $tree
QMatrix qMatrix = $qMatrix
Vector<Rate> branchRates ~ RelaxedClock(
  base=LogNormal(mean=1.0, logSd=$logSd),
  clockRate~$clockRate, 
  tree
)

Alignment alignment ~ PhyloCTMC(
  tree, qMatrix, branchRates
) observed as data`

const CONFIG = {
  experimentName: 'PhyloCTMC Analysis',
  defaults: {
    taxa: 'taxa(data)',
    alignment: 'data',
    tree: 'tree',
  },
  placeholders: {
    '$alignment': { type: 'Alignment<Character>', name: 'Alignment',          description: 'Choose the nucleotide sequence alignment.' },
    '$tree':      { type: 'Distribution<Tree>',   name: 'Tree Prior',         description: 'Specify a prior distribution over the tree topology and branch lengths.' },
    '$qMatrix':   { type: 'QMatrix',              name: 'Substitution Model', description: 'Select a substitution model for the alignment.' },
    '$logSd':     { type: 'PositiveReal',         name: 'Log Sd',             description: 'Log standard deviation of the relaxed clock log-normal.' },
    '$clockRate': { type: 'Distribution<Rate>',   name: 'Clock Rate',         description: 'Prior distribution over the mean clock rate.' },
  },
  groups: [
    { name: 'Relaxed Clock', description: 'Parameters of the relaxed molecular clock (uncorrelated log-normal).', placeholders: ['$logSd', '$clockRate'] },
  ],
}

export function Demo() {
  return (
    <main className="min-h-screen bg-background text-foreground">
      <header className="w-full border-b border-accent/20 bg-gray-50 p-4 backdrop-blur-md">
        <div className="mx-auto flex w-full max-w-7xl items-center">
          <Link className="text-2xl font-bold tracking-tight flex-1" href="/">
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
