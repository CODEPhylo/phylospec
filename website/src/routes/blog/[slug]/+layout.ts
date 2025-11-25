export const load = async ({ params }) => {
	const post = await import(`$lib/blog/${params.slug}.md`);

	return {
		PostContent: post.default,
		meta: { ...post.metadata, slug: params.slug }
	};
};
