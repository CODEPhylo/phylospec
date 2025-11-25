<script lang="ts">
	let { data } = $props();
	let { PostContent, meta } = $derived(data);
</script>

<div
	class="prose prose-h1:text-accent prose-h2:text-accent prose-headings:font-sans font-serif text-[1.1rem] w-full max-w-[700px] py-16 px-4"
>
	{#if meta.date || meta.author}
		<div class="not-prose mb-8">
			<div class="flex gap-3 items-center">
				{#if meta.date}
					<p class="text-sm text-gray-500">{meta.date}</p>
				{/if}
				{#if meta.author}
					<span class="text-sm text-gray-400">•</span>
					<p class="text-sm text-gray-500">{meta.author}</p>
				{/if}
			</div>
		</div>
	{/if}
	<PostContent />
</div>
