<script>
	import { base } from '$app/paths';
</script>

# Prototypes

PhyloSpec has working prototype tools that are being developed to guide the design of the component library format and core components. Furthermore, they allow to catch potential challenges related to integration into engines early on.

!> Some of the prototypes like the converters are *not* aiming to be production-ready. They do not represent how things will work down the line, but are merely part of the process to learn about the problem at hand.

## Language Tools

**Parser and Type Checker** — Validates syntax and ensures type safety before model execution (<a href="https://github.com/CODEPhylo/phylospec/pull/9" target="_blank">PR #9</a>, <a href="https://github.com/CODEPhylo/phylospec/pull/11" target="_blank">PR #11</a>, <a href="https://github.com/CODEPhylo/phylospec/pull/17" target="_blank">PR #17</a>)

**Language Server Protocol** — Provides IDE features including syntax highlighting, auto-completion, type checking, hover information, and error diagnostics (<a href="https://github.com/CODEPhylo/phylospec/pull/12" target="_blank">PR #12</a>)

**VS Code Extension** — Complete editing experience for PhyloSpec files using the LSP (<a href="https://github.com/CODEPhylo/phylospec/tree/main/tools/vscode" target="_blank">check it out</a>)

<video
		src="vscode.mp4"
        autoplay
        loop
        playbackRate="3"
>
</video>

**Template-based GUI** — a visual form builder for composing PhyloSpec models. Users can fill in placeholders interactively and export a ready-to-run `.phylospec` file (<a href="https://github.com/CODEPhylo/phylospec/pull/36" target="_blank">PR #36</a>)

<img src="{base}/template-gui.png" class="w-10/10" alt="Screenshot of the template-based GUI" />

## Runner

**PhyloRun** — Executes PhyloSpec models in RevBayes and BEAST 2 (<a href="https://github.com/tochsner/phylorun/tree/main" target="_blank">Try it out</a>)


## Converters & Parsers

**BEAST 2.8 PhyloSpec Parser** — Allows PhyloSpec models to run in BEAST 2.8 (<a href="https://github.com/CODEPhylo/phylospec/pull/30" target="_blank">PR #30</a> and <a href="https://github.com/CODEPhylo/phylospec/pull/32" target="_blank">PR #32</a>)

**PhyloSpec to RevBayes** — Allows PhyloSpec models to run in RevBayes (<a href="https://github.com/CODEPhylo/phylospec/pull/16" target="_blank">PR #16</a>, <a href="https://github.com/tochsner/phylorun/tree/main#run-phylospec-analyses" target="_blank">try it out</a>)

**PhyloSpec to LPhy** — Allows PhyloSpec models to run in BEAST 2 using LPhyBEAST (<a href="https://github.com/CODEPhylo/phylospec/pull/14" target="_blank">PR #14</a>, <a href="https://github.com/tochsner/phylorun/tree/main#run-phylospec-analyses" target="_blank">try it out</a>)

**PhyloSpec to JSON** — Simplifies integration into inference engines (<a href="https://github.com/CODEPhylo/phylospec/pull/17" target="_blank">PR #17</a>)
