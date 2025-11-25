import { mdsvex, escapeSvelte } from 'mdsvex';
import adapter from '@sveltejs/adapter-static';
import { createHighlighter } from 'shiki'
import hint from 'remark-hint';

import phylospec from './src/lib/phylospec.json' with { type: 'json' };
import nord from './src/lib/themes/nord.json' with { type: 'json' };

const highlighter = await createHighlighter({
	themes: [nord],
	langs: [phylospec, 'javascript', 'json', 'java', 'bash', 'bibtex'],
});

/** @type {import('@sveltejs/kit').Config} */
const config = {
	kit: {
		adapter: adapter({
			pages: 'build',
			assets: 'build',
			fallback: '404.html',
			precompress: false,
			strict: true
		}),
		paths: {
			base: process.argv.includes('dev') ? '' : '/phylospec'
		}
	},
	preprocess: [mdsvex({
		extensions: ['.md'],
		highlight: {
			highlighter: async (code, lang = 'text') => {
				const html = escapeSvelte(highlighter.codeToHtml(code, { lang, theme: nord }));
				return `{@html \`${html}\` }`;
			}
		},
		remarkPlugins: [hint]
	})],
	extensions: ['.svelte', '.svx', '.md']
};

export default config;
