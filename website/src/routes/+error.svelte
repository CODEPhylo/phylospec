<script>
	import { page } from '$app/stores';
	import { base } from '$app/paths';
</script>

<svelte:head>
	<title>{$page.status}: {$page.error?.message}</title>
</svelte:head>

<div class="flex flex-col items-center justify-center min-h-[60vh] px-4">
	<h1 class="text-6xl font-bold text-accent mb-4">{$page.status}</h1>
	<h2 class="text-2xl font-semibold mb-4">
		{#if $page.status === 404}
			Page Not Found
		{:else}
			{$page.error?.message || 'An error occurred'}
		{/if}
	</h2>
	<p class="text-gray-600 mb-8 text-center max-w-md">
		{#if $page.status === 404}
			The page you're looking for doesn't exist or has been moved.
		{:else}
			Something went wrong while loading this page.
		{/if}
	</p>
	<a
		href="{base}/"
		class="px-6 py-3 bg-accent text-white rounded-lg hover:bg-accent/90 transition-colors"
	>
		Go back home
	</a>
</div>
