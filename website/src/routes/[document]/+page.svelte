<script lang="ts">
	let { data } = $props();
	let { PostContent } = $derived(data);
</script>

<div class="prose prose-h1:text-accent w-full max-w-[700px] py-16 px-4">
	<PostContent />
</div>
