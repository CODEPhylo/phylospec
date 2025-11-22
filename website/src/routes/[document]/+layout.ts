export const load = async ({ params }) => {
	const post = await import(`$lib/content/${params.document}.md`);

	return {
		PostContent: post.default,
		meta: { ...post.metadata, slug: params.document }
	};
};
