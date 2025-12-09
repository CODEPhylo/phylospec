<script lang="ts">
	import coreComponents from '$lib/assets/core-components.json';
</script>

<div
	class="prose prose-h1:text-accent prose-h2:text-accent prose-headings:font-sans font-serif text-[1.1rem] w-full max-w-[700px] py-16 px-4"
>
	<h1>Core Component Library</h1>

	<p>
		PhyloSpec aims to start a conversation about standard model components, common assumptions, and
		best practices in the field of phylogenetics. The result of this will be the <strong
			>Core Component Library</strong
		>, a set of model components which are considered to be common and well-established.
	</p>

	<p>
		The following represents <strong>Draft 12.2025</strong> of the core components. The prototype tools
		implement most of these core components.
	</p>

	<p class="hint tip">Add your feedback in the <a href="https://github.com/CODEPhylo/phylospec/discussions/23">GitHub Discussion</a> of this draft!</p>

	<h2>Types</h2>

	<div class="space-y-3">
		{#each coreComponents.componentLibrary.types as type}
			<div class="border border-accent-light rounded-lg p-3">
				<h3 class="text-lg font-sans font-semibold mt-0 mb-0.5 flex items-center gap-0">
					{#if type.typeParameters && type.typeParameters.length > 0}
						{type.name}{'<'}{type.typeParameters.join(', ')}{'>'}
					{:else}
						{type.name}
					{/if}
				</h3>
				{#if type.description}
					<p class="text-gray-600 text-sm mb-0">{type.description}</p>
				{/if}
				<div class="text-sm">
					{#if type.extends}
						<div class="mt-1.5">
							<span class="font-medium">Extends:</span>
							<code class="bg-gray-100 px-1 rounded">{type.extends}</code>
						</div>
					{/if}
					{#if type.alias}
						<div class="mt-1.5">
							<span class="font-medium">Alias for:</span>
							<code class="bg-gray-100 px-1 rounded">{type.alias}</code>
						</div>
					{/if}
				</div>
			</div>
		{/each}
	</div>

	<h2>Generators</h2>

	<div class="space-y-6">
		{#each coreComponents.componentLibrary.generators as generator}
			<div class="border border-accent-light rounded-xl p-4">
				<h3 class="text-xl font-sans font-semibold mt-0 mb-1 flex items-center gap-0">
					{#if generator.typeParameters && generator.typeParameters.length > 0}
						{generator.name}{'<'}{generator.typeParameters.join(', ')}{'>'}
					{:else}
						{generator.name}
					{/if}
				</h3>
				{#if generator.description}
					<p class="text-gray-600 text-base mb-0">{generator.description}</p>
				{/if}
				<div class="text-sm">
					<div class="mt-2">
						<span class="font-medium">Generated Type:</span>
						<code class="bg-gray-100 px-1 rounded">{generator.generatedType}</code>
					</div>
					{#if generator.arguments && generator.arguments.length > 0}
						<div class="mt-2">
							<span class="font-medium">Arguments:</span>
							<ul class="list-disc list-inside ml-2 mt-1">
								{#each generator.arguments as arg}
									<li>
										<code class="bg-gray-100 px-1 rounded">{arg.name}</code>:
										<code class="bg-gray-100 px-1 rounded">{arg.type}</code>
										{#if arg.required}
											<span class="font-medium ml-1">(required)</span>
										{:else if (arg as any).recommended}
											<span class="font-medium ml-1">(recommended)</span>
										{:else}
											<span class="text-gray-500 ml-1">(optional)</span>
										{/if}
										{#if (arg as any).default !== undefined}
											<span class="text-gray-600 ml-1"
												>[default: <code class="bg-gray-100 px-1 rounded"
													>{String((arg as any).default)}</code
												>]</span
											>
										{/if}
										{#if (arg as any).dimension}
											<span class="text-gray-600 ml-1"
												>[dimension: <code class="bg-gray-100 px-1 rounded"
													>{typeof (arg as any).dimension === 'number'
														? (arg as any).dimension
														: (arg as any).dimension}</code
												>]</span
											>
										{/if}
										{#if arg.description}
											<span class="text-gray-600"> - {arg.description}</span>
										{/if}
									</li>
								{/each}
							</ul>
						</div>
					{/if}
				</div>
			</div>
		{/each}
	</div>
</div>
