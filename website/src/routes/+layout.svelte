<script>
	import '../app.css';
	import { base } from '$app/paths';

	let { children } = $props();
	let mobileMenuOpen = $state(false);

	function toggleMenu() {
		mobileMenuOpen = !mobileMenuOpen;
	}
</script>

<svelte:head>
	<title>PhyloSpec</title>
	<meta
		name="description"
		content="A standardized way to describe phylogenetic model components, common assumptions, and best practices in the field of phylogenetics."
	/>
	<meta name="robots" content="index,follow" />
</svelte:head>

<div class="bg-background font-sans text-dark min-h-lvh w-lvw flex flex-col items-center pt-16">
	<header
		class="w-full bg-light-gray/80 backdrop-blur-md p-4 flex items-center fixed top-0 left-0 right-0 z-10 border-b border-gray-200"
	>
		<a class="text-2xl font-bold flex-1" href="{base}/">
			<span class="text-accent">Phylo</span><span class="text-gray-900">Spec</span>
		</a>

		<!-- Desktop Navigation -->
		<nav class="hidden lg:flex gap-10 mx-5">
			<a href="{base}/">Introduction</a>
			<a href="{base}/specification">Specification</a>
			<a href="{base}/components">Core Components</a>
			<a href="{base}/language">Modeling Language</a>
			<a href="{base}/prototypes">Prototypes</a>
			<a href="{base}/blog">Blog</a>
		</nav>

		<!-- Hamburger Menu Button (Mobile Only) -->
		<button
			onclick={toggleMenu}
			class="lg:hidden flex flex-col gap-1.5 p-2"
			aria-label="Toggle menu"
		>
			<span
				class="w-6 h-0.5 bg-gray-900 transition-all duration-300 {mobileMenuOpen
					? 'rotate-45 translate-y-2'
					: ''}"
			></span>
			<span
				class="w-6 h-0.5 bg-gray-900 transition-all duration-300 {mobileMenuOpen
					? 'opacity-0'
					: ''}"
			></span>
			<span
				class="w-6 h-0.5 bg-gray-900 transition-all duration-300 {mobileMenuOpen
					? '-rotate-45 -translate-y-2'
					: ''}"
			></span>
		</button>
	</header>

	<!-- Mobile Navigation Menu -->
	{#if mobileMenuOpen}
		<nav
			class="lg:hidden fixed top-16 left-0 right-0 bg-light-gray/80 backdrop-blur-md z-10 border-b border-gray-200"
		>
			<div class="flex flex-col p-4 gap-4">
				<a href="{base}/" onclick={toggleMenu}>Introduction</a>
				<a href="{base}/specification" onclick={toggleMenu}>Specification</a>
				<a href="{base}/components" onclick={toggleMenu}>Core Components</a>
				<a href="{base}/language" onclick={toggleMenu}>Modeling Language</a>
				<a href="{base}/prototypes" onclick={toggleMenu}>Prototypes</a>
				<a href="{base}/blog" onclick={toggleMenu}>Blog</a>
			</div>
		</nav>
	{/if}

	{@render children()}
</div>
