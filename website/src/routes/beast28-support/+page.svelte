<script lang="ts">
	import coreComponents from '$lib/assets/core-components.json';
	import beastSupport from '$lib/assets/beast28-support.csv?raw';

	const supportMap = new Map<string, { level: number; limitations: string }>();
	for (const line of beastSupport.trim().split('\n').slice(1)) {
		const [name, level, limitations = ''] = line.split(',');
		supportMap.set(name, { level: parseInt(level), limitations });
	}

	// collapse overloads
	const seen = new Set<string>();
	const generators = coreComponents.componentLibrary.generators.filter((g) => {
		if (seen.has(g.name)) return false;
		seen.add(g.name);
		return true;
	});

	const supported = generators.filter((g) => (supportMap.get(g.name)?.level ?? 1) >= 2).length;
	const pct = Math.round((supported / generators.length) * 100);

	function badgeLabel(level: number) {
		if (level === 3) return 'Full';
		if (level === 2) return 'Partial';
		return 'None';
	}
</script>

<div
	class="prose prose-h1:text-accent prose-h2:text-accent prose-headings:font-sans font-serif text-[1.1rem] w-full max-w-[700px] py-16 px-4"
>
	<h1>BEAST 2.8 Parser Support</h1>

	<p>Support level of each PhyloSpec core generator in the BEAST 2.8 parser prototype.</p>

	<div class="not-prose mb-6">
		<div class="flex justify-between text-sm text-gray-600 mb-1.5">
			<span>At least partial support</span>
			<span class="font-semibold">{supported} / {generators.length} &nbsp;({pct}%)</span>
		</div>
		<div class="w-full rounded-full overflow-hidden" style="height:10px;background:#e5e7eb">
			<div style="height:10px;width:{pct}%;background:#34d399;transition:width 0.3s"></div>
		</div>
	</div>

	<div class="not-prose overflow-hidden rounded-md border border-gray-200">
		<table class="w-full text-sm">
			<thead>
				<tr class="bg-gray-50 border-b border-gray-200">
					<th class="text-left px-4 py-4 font-semibold text-gray-700">Generator</th>
					<th class="text-left px-4 py-4 font-semibold text-gray-700">BEAST 2.8</th>
					<th class="text-left px-4 py-4 font-semibold text-gray-700">Limitations</th>
				</tr>
			</thead>
			<tbody>
				{#each generators as g, i}
					{@const entry = supportMap.get(g.name) ?? { level: 1, limitations: '' }}
					{@const level = entry.level}
					<tr class={i % 2 === 0 ? 'bg-white' : 'bg-gray-50'}>
						<td class="px-4 py-1 font-mono font-medium text-gray-900">{g.name}</td>
						<td class="px-4 py-1">
							<span
								class="inline-block border rounded-full px-2.5 py-0.5 text-xs font-semibold"
								style={level === 3
									? 'background:#d1fae5;color:#065f46;border-color:#6ee7b7'
									: level === 2
										? 'background:#fef3c7;color:#92400e;border-color:#fcd34d'
										: 'background:#fee2e2;color:#b91c1c;border-color:#fca5a5'}
								>{badgeLabel(level)}</span
							>
						</td>
						<td class="px-4 py-1 text-gray-500 text-xs">
							{@html entry.limitations}
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
</div>
