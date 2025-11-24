import { mdsvex, escapeSvelte } from 'mdsvex';
import adapter from '@sveltejs/adapter-auto';
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
		adapter: adapter()
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
