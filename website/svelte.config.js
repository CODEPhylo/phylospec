import { mdsvex, escapeSvelte } from 'mdsvex';
import adapter from '@sveltejs/adapter-auto';
import { createHighlighter } from 'shiki'
import fs from 'fs';
import hint from 'remark-hint';

const theme = JSON.parse(fs.readFileSync('src/lib/themes/nord.json', 'utf8'));
const phylospec = JSON.parse(fs.readFileSync('src/lib/phylospec.json', 'utf8'))
const highlighter = await createHighlighter({
	themes: [theme],
	langs: [phylospec, 'javascript', 'json', 'java', 'bash', 'bibtex'],
})

/** @type {import('@sveltejs/kit').Config} */
const config = {
	kit: {
		adapter: adapter()
	},
	preprocess: [mdsvex({
		extensions: ['.md'],
		highlight: {
			highlighter: async (code, lang = 'text') => {
				const html = escapeSvelte(highlighter.codeToHtml(code, { lang, theme }));
				return `{@html \`${html}\` }`;
			}
		},
		remarkPlugins: [hint]
	})],
	extensions: ['.svelte', '.svx', '.md']
};

export default config;
