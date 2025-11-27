<script lang="ts">
	let { data } = $props();
	let { PostContent, meta } = $derived(data);
</script>

<div
	class="prose prose-h1:text-accent prose-h2:text-accent prose-headings:font-sans font-serif text-[1.1rem] w-full max-w-[700px] py-16 px-4"
>
	{#if meta.date || meta.author || meta.readingTime}
		<div class="not-prose mb-6">
			<div class="flex gap-3 items-center italic text-gray-500 text-base font-sans">
				{#if meta.date}
					<p>{meta.date}</p>
				{/if}
				{#if meta.author}
					<span>•</span>
					<p>{meta.author}</p>
				{/if}
				{#if meta.readingTime}
					<span>•</span>
					<p>{meta.readingTime} min read</p>
				{/if}
			</div>
		</div>
	{/if}
	<PostContent />
</div>
