function calculateReadingTime(content: string): number {
	// Remove markdown syntax and HTML tags for accurate word count
	const text = content
		.replace(/---[\s\S]*?---/g, '') // Remove frontmatter
		.replace(/```[\s\S]*?```/g, '') // Remove code blocks
		.replace(/`[^`]*`/g, '') // Remove inline code
		.replace(/<[^>]*>/g, '') // Remove HTML tags
		.replace(/[#*_~\[\]()]/g, '') // Remove markdown syntax
		.replace(/\s+/g, ' ') // Normalize whitespace
		.trim();

	const words = text.split(/\s+/).length;
	const wordsPerMinute = 200;
	return Math.ceil(words / wordsPerMinute);
}

export const load = async ({ params }) => {
	const post = await import(`$lib/blog/${params.slug}.md`);

	// Import raw content to calculate reading time
	const allPostFiles = import.meta.glob('$lib/blog/*.md', { as: 'raw', eager: true });
	const postPath = `/src/lib/blog/${params.slug}.md`;
	const content = allPostFiles[postPath] || '';
	const readingTime = calculateReadingTime(content);

	return {
		PostContent: post.default,
		meta: { ...post.metadata, slug: params.slug, readingTime }
	};
};
