---
title: "The Way of PhyloSpec"
date: "2025-11-27"
author: "Tobia Ochsner"
---

# The Way of PhyloSpec

Designing a phylogenetic modeling language involves making many decisions. Existing languages and tools offer countless variations in syntax, abstraction levels, model parameterizations, and best practices. In a vacuum, no perfect language exists—it emerges only when you have clear priorities and constraints.

This post is an attempt to outline my personal view of what we're trying to achieve with PhyloSpec. This should guide us in making decisions, especially the ones that would otherwise just come down to subjective personal preference.

## Researchers First

We __prioritize researchers__ who run phylogenetic experiments on their data. Bayesian phylogenetic software is just one of many tools in their toolbox. Hence, we want to make it easy to *correctly* apply Bayesian phylogenetics in their research.

We __aim to cover the most commonly used models__ first. If you are looking for a language with the flexibility to implement anything you can imagine, look no further than <a href="https://revbayes.github.io/" target="_blank">Rev</a>.

## Models over Machinery

At the core of PhyloSpec is a modeling language. It should allow researchers to clearly describe the experiment they want to perform.

The chosen abstraction level should __reflect the modeling choices and assumptions made by the researcher__. All irrelevant mathematical or technical details are abstracted away or stored in a secondary block.

Furthermore, the language should __help researchers understand the Bayesian phylogenetic framework__ by making core concepts explicit (like priors, substitution models, site and clock models, and the phylogenetic CTMC.)

## Driven by Consensus

The [core components](../components) __are defined by a working group of people in the field__. The components and their parameterizations should represent what the community currently agrees on.

Most components have already been implemented in many different places. This gives us a chance to learn from these implementations.

This discussion about standards also benefits educators and students, since it means __we can unify how we define and talk about common concepts__.

## It's an Ecosystem

PhyloSpec attempts to break down existing fragmentation in the field. It tries to create a thriving ecosystem of shared tools, model repositories, <a href="https://www.phylodata.com" target="_blank">experiment repositories</a>, educational material, documentation, and workshops. Who knows, we might have a PhyloSpec conference at some point!

## Layered Extensibility

PhyloSpec should start small and grow carefully. The core components are stable, consensus-driven, and compatible with multiple inference engines.

However, is should be __easy to incorporate additional engine-specific components and features__ in models.

There must be a clearly defined path to add external components to the core. This keeps the language evolving and enables innovation without fragmenting the ecosystem or losing stability.

## Tooling as a First-Class Citizen

PhyloSpec should come with parsers, converters, scientific and technical linters, formatters, visualizers, and migration tools out of the box. We aim to deliver a smooth experience for both researchers and engine developers implementing the standard.

## Language Design

While components are flexible and community-driven, the __language design is opinionated__. There should be one preferred way to express most ideas. The syntax should be as concise as possible, but not at the expense of readability or clarity. Naming should be ultra-consistent, descriptive, and focused on concepts rather than mathematical or engine-specific jargon. We're paying special attention to helpful and educational error messages.

---

While these points don't cover everything and are rather vague, they still represent how I currently think about the project. I'm happy to discuss all of it in the GitHub Discussion (@Tobia link tbd) of this post!