<script>
	import '../app.css';
	import favicon from '$lib/assets/favicon.svg';

	let { children } = $props();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

<div class="bg-background font-sans text-dark min-h-lvh w-lvw flex flex-col items-center">
	<header class="w-full bg-light-gray p-4">
		<a class="text-2xl font-bold text-gray-800" href="/">
			<span class="text-accent">Phylo</span>Spec
		</a>
	</header>

	{@render children()}
</div>
