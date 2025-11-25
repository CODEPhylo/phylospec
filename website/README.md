# PhyloSpec Website

Website for [PhyloSpec](https://github.com/CODEPhylo/phylospec) - a specification for phylogenetic modeling components.

## Tech Stack

- **SvelteKit** - Web framework with static site generation
- **MDsveX** - Markdown preprocessing with Svelte components
- **Tailwind CSS** - Utility-first styling
- **Shiki** - Syntax highlighting with custom PhyloSpec language support

## Prerequisites

- Node.js 18+ (or Bun)
- npm/pnpm/yarn/bun

## Getting Started

```sh
# Install dependencies
npm install

# Start development server
npm run dev

# Open in browser
npm run dev -- --open
```

## Development

### Available Scripts

```sh
npm run dev          # Start dev server
npm run build        # Build for production
npm run preview      # Preview production build
npm run check        # Run Svelte type checking
npm run check:watch  # Run type checking in watch mode
npm run format       # Format code with Prettier
npm run lint         # Check code formatting
```

### Project Structure

```
website/
├── src/
│   ├── lib/
│   │   ├── assets/              # Assets
│   │   ├── content/             # Markdown content (language.md, specification.md, etc.)
│   │   ├── schema/              # JSON schemas
│   │   ├── themes/              # Syntax highlighting (nord.json, phylospec.json)
│   └── routes/
│       ├── [[document]]/        # Dynamic markdown document routes
│       ├── components/          # Components page
│       ├── +layout.svelte       # Root layout
├── static/                      # Static assets (favicons, videos, manifest)
└── svelte.config.js             # SvelteKit + MDsveX configuration
```

### Key Features

- **MDsveX**: Write pages in Markdown with embedded Svelte components (`.md` files)
- **Custom Syntax Highlighting**: PhyloSpec language support via Shiki
- **Static Deployment**: Pre-rendered for GitHub Pages at `/phylospec` base path
- **Remark Hints**: Enhanced markdown with hint blocks

## Building

```sh
npm run build
```

Outputs static site to `build/` directory, configured for deployment to GitHub Pages with base path `/phylospec`.

## Deployment

The site is automatically deployed to GitHub Pages via GitHub Actions. See repository workflows for deployment configuration.
