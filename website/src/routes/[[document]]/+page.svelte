<script lang="ts">
	let { data } = $props();
	let { PostContent } = $derived(data);
</script>

<div
	class="prose prose-h1:text-accent prose-h2:text-accent prose-headings:font-sans font-serif text-[1.1rem] w-full max-w-[700px] py-16 px-4"
>
	<PostContent />
</div>
