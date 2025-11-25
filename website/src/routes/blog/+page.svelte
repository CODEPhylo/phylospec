<script lang="ts">
	import { base } from '$app/paths';

	let { data } = $props();
	let { posts } = $derived(data);
</script>

<div class="w-full max-w-[700px] py-16 px-4">
	<h1 class="text-4xl font-bold mb-2 text-accent">Blog</h1>
	<p class="text-gray-600 mb-12">Updates, insights, and progress on PhyloSpec</p>

	<div class="space-y-4">
		{#each posts as post}
			<article class="pb-4">
				<a href="{base}/blog/{post.slug}" class="group">
					<h2
						class="text-xl font-bold mb-1 text-gray-900 group-hover:text-accent transition-colors"
					>
						{post.title}
					</h2>
					<div class="flex gap-2 items-center">
						{#if post.date}
							<p class="text-sm text-gray-500">{post.date}</p>
						{/if}
						{#if post.author}
							<span class="text-sm text-gray-400">•</span>
							<p class="text-sm text-gray-500">{post.author}</p>
						{/if}
					</div>
				</a>
			</article>
		{/each}
	</div>
</div>
