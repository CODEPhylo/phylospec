export const load = async () => {
	const allPostFiles = import.meta.glob('$lib/blog/*.md');
	const posts = [];

	for (const path in allPostFiles) {
		const post = await allPostFiles[path]();
		const slug = path.split('/').pop()?.replace('.md', '');

		posts.push({
			slug,
			...post.metadata
		});
	}

	// Sort posts by date, newest first
	posts.sort((a, b) => {
		const dateA = new Date(a.date || 0);
		const dateB = new Date(b.date || 0);
		return dateB.getTime() - dateA.getTime();
	});

	return { posts };
};
