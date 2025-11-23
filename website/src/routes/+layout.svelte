<script>
	import '../app.css';
	import favicon from '$lib/assets/favicon.svg';

	let { children } = $props();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

<div class="bg-background font-sans text-dark min-h-lvh w-lvw flex flex-col items-center">
	<header class="w-full bg-light-gray p-4 flex items-center">
		<a class="text-2xl font-bold text-gray-800 flex-1" href="/">
			<span class="text-accent">Phylo</span>Spec
		</a>

		<nav class="flex gap-8">
			<a href="/">Introduction</a>
			<a href="/specification">Specification</a>
			<a href="/components">Core Components</a>
			<a href="/language">Modeling Language</a>
			<a href="/prototypes">Prototypes</a>
		</nav>
	</header>

	{@render children()}
</div>
