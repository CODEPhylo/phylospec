<script>
	import '../app.css';
	import favicon from '$lib/assets/favicon.svg';

	let { children } = $props();
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
</svelte:head>

<div class="bg-background font-sans text-dark min-h-lvh w-lvw flex flex-col items-center pt-16">
	<header
		class="w-full bg-light-gray/80 backdrop-blur-md p-4 flex items-center fixed top-0 left-0 right-0 z-10"
	>
		<a class="text-2xl font-bold flex-1" href="/">
			<span class="text-accent">Phylo</span><span class="text-gray-900">Spec</span>
		</a>

		<nav class="flex gap-10">
			<a href="/">Introduction</a>
			<a href="/specification">Specification</a>
			<a href="/components">Core Components</a>
			<a href="/language">Modeling Language</a>
			<a href="/prototypes">Prototypes</a>
		</nav>
	</header>

	{@render children()}
</div>
